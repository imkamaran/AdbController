package com.adbcontroller.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.adbcontroller.MainActivity
import com.adbcontroller.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface

/**
 * Process-wide wireless-debugging pairing state, shared by the UI and the notification that
 * lets the user pair this phone with itself (the pairing dialog in Settings closes as soon as
 * another app comes to the front, so the code is typed into a notification instead).
 */
object PairingController {

    sealed class SelfPairingEvent {
        data class Paired(val host: String) : SelfPairingEvent()
        data class Failed(val message: String) : SelfPairingEvent()
    }

    private const val CHANNEL_ID = "pairing"
    private const val NOTIFICATION_ID = 1001
    const val KEY_PAIRING_CODE = "pairing_code"

    private var initialized = false
    lateinit var discovery: AdbServiceDiscovery
        private set
    lateinit var wireless: WirelessDebugging
        private set

    private val _selfPairing = MutableStateFlow<SelfPairingEvent?>(null)
    val selfPairing: StateFlow<SelfPairingEvent?> = _selfPairing.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        discovery = AdbServiceDiscovery(app)
        wireless = WirelessDebugging(AdbCrypto.get(app))
        initialized = true
    }

    fun consumeSelfPairing() {
        _selfPairing.value = null
    }

    /** IP addresses of this phone, used to recognise its own wireless-debugging services. */
    fun localAddresses(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .toSet()
    }.getOrDefault(emptySet())

    private fun localPairingService(): AdbService? {
        val local = localAddresses()
        return discovery.services.value.firstOrNull { it.type == AdbService.Type.PAIRING && it.host in local }
    }

    /** Posts the notification the user types this phone's pairing code into. */
    fun showCodeNotification(context: Context, text: String = DEFAULT_PROMPT) {
        init(context)
        discovery.start()
        notify(context, text, withCodeInput = true, ongoing = true)
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Called from [PairingCodeReceiver] on a background thread. */
    fun pairThisPhone(context: Context, code: String) {
        init(context)
        discovery.start()
        notify(context, "Pairing…", withCodeInput = false, ongoing = true)

        var service = localPairingService()
        val deadline = System.currentTimeMillis() + 8_000
        while (service == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            service = localPairingService()
        }
        if (service == null) {
            val message = "Couldn't find the pairing dialog. Open Wireless debugging → " +
                "Pair device with pairing code, then enter the code here."
            notify(context, message, withCodeInput = true, ongoing = true)
            _selfPairing.value = SelfPairingEvent.Failed(message)
            return
        }

        try {
            wireless.pair(service.host, service.port, code)
            notify(context, "Paired! Connecting… Open ADB Controller to continue.", withCodeInput = false, ongoing = false)
            _selfPairing.value = SelfPairingEvent.Paired(service.host)
        } catch (e: Exception) {
            val message = "Pairing failed: ${e.message ?: "check the code"}. Try again with the current code."
            notify(context, message, withCodeInput = true, ongoing = true)
            _selfPairing.value = SelfPairingEvent.Failed(message)
        }
    }

    private fun notify(context: Context, text: String, withCodeInput: Boolean, ongoing: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Wireless debugging pairing", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Pair this phone")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(openApp)

        if (withCodeInput) {
            val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
                .setLabel("6-digit pairing code")
                .build()
            // RemoteInput needs a mutable PendingIntent; the intent is explicit, so this is safe.
            val replyIntent = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, PairingCodeReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Enter pairing code", replyIntent)
                    .addRemoteInput(remoteInput)
                    .build()
            )
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private const val DEFAULT_PROMPT =
        "Open Settings → Developer options → Wireless debugging → Pair device with pairing code, " +
            "then enter the 6-digit code here."
}

/** Receives the pairing code typed into the notification. */
class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PairingController.KEY_PAIRING_CODE)
            ?.toString()
            ?.filter { it.isDigit() }
            .orEmpty()
        if (code.isEmpty()) return

        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                PairingController.pairThisPhone(appContext, code)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
