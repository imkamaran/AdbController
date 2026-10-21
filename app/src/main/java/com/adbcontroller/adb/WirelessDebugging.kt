package com.adbcontroller.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/**
 * Android 11+ "Wireless debugging" support, built on libadb-android, which implements the
 * SPAKE2-over-TLS pairing protocol and TLS connections.
 *
 * After pairing, the device is switched into classic TCP mode (`adb tcpip`) so the rest of the
 * app can use its normal ADB connection.
 */
class WirelessDebugging(private val crypto: AdbCrypto) {

    private inner class Manager : AbsAdbConnectionManager() {
        init {
            // Wireless debugging only exists on Android 11 (API 30) and later.
            setApi(Build.VERSION_CODES.R)
            setTimeout(20, TimeUnit.SECONDS)
        }

        override fun getPrivateKey(): PrivateKey = crypto.privateKey
        override fun getCertificate(): Certificate = crypto.certificate()
        override fun getDeviceName(): String = "ADB Controller on ${Build.MODEL}"
    }

    /** Pairs with the device's pairing service. Throws if the device rejects the code. */
    fun pair(host: String, pairingPort: Int, pairingCode: String) {
        Manager().use { manager ->
            if (!manager.pair(host, pairingPort, pairingCode.trim())) {
                throw IOException("The device rejected the pairing. Check the code and try again.")
            }
        }
    }

    /**
     * Connects to a paired device's wireless-debugging port and switches its ADB daemon into
     * TCP mode on [tcpPort]. Returns the daemon's response text.
     */
    fun enableTcpMode(host: String, connectPort: Int, tcpPort: Int = 5555): String {
        Manager().use { manager ->
            if (!manager.connect(host, connectPort)) {
                throw IOException("Couldn't connect to $host:$connectPort")
            }
            val stream = manager.openStream("tcpip:$tcpPort")
            val response = StringBuilder()
            try {
                stream.openInputStream().use { input ->
                    val buffer = ByteArray(256)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        response.append(String(buffer, 0, n, Charsets.UTF_8))
                    }
                }
            } catch (_: IOException) {
                // adbd restarts into TCP mode and drops this connection, which ends the read.
            }
            return response.toString().trim()
        }
    }
}

data class AdbService(
    val name: String,
    val type: Type,
    val host: String,
    val port: Int
) {
    enum class Type { PAIRING, CONNECT }
}

/**
 * Finds devices on the local network that advertise wireless-debugging pairing or connect
 * services over mDNS (the same discovery Android Studio and Shizuku rely on).
 */
class AdbServiceDiscovery(context: Context) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val pendingResolves = ArrayDeque<Pair<NsdServiceInfo, AdbService.Type>>()
    private var resolving = false
    private val lock = Any()

    private val _services = MutableStateFlow<List<AdbService>>(emptyList())
    val services: StateFlow<List<AdbService>> = _services.asStateFlow()

    fun start() {
        synchronized(lock) {
            if (discoveryListeners.isNotEmpty()) return
            discover(PAIRING_SERVICE, AdbService.Type.PAIRING)
            discover(CONNECT_SERVICE, AdbService.Type.CONNECT)
        }
    }

    fun stop() {
        synchronized(lock) {
            discoveryListeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
            discoveryListeners.clear()
            pendingResolves.clear()
            resolving = false
            _services.value = emptyList()
        }
    }

    private fun discover(serviceType: String, type: AdbService.Type) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(lock) {
                    pendingResolves.addLast(info to type)
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _services.value = _services.value.filterNot { it.name == info.serviceName && it.type == type }
            }
        }
        discoveryListeners += listener
        runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // NsdManager only resolves one service at a time on older Android versions.
    private fun resolveNext() {
        if (resolving) return
        val (info, type) = pendingResolves.removeFirstOrNull() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = finishResolve()

            override fun onServiceResolved(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = info.host?.hostAddress
                if (host != null && info.port > 0) {
                    val service = AdbService(info.serviceName, type, host, info.port)
                    _services.value = _services.value
                        .filterNot { it.name == service.name && it.type == type } + service
                }
                finishResolve()
            }
        })
    }

    private fun finishResolve() {
        synchronized(lock) {
            resolving = false
            resolveNext()
        }
    }

    companion object {
        private const val PAIRING_SERVICE = "_adb-tls-pairing._tcp"
        private const val CONNECT_SERVICE = "_adb-tls-connect._tcp"
    }
}
