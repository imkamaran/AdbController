package com.adbcontroller.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ConnectionType { WIFI, USB }

data class RecentDevice(
    val id: String,
    val type: ConnectionType,
    val host: String = "",
    val port: Int = 5555,
    val model: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val serial: String = "",
    val nickname: String = "",
    val lastConnected: Long = 0L,
    val connectCount: Int = 1
) {
    val address: String get() = "$host:$port"

    val displayName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            model.isNotBlank() -> {
                val maker = manufacturer.replaceFirstChar { it.uppercase() }
                if (maker.isBlank() || model.startsWith(maker, ignoreCase = true)) model
                else "$maker $model"
            }
            type == ConnectionType.WIFI -> address
            else -> "USB device"
        }

    companion object {
        fun wifiId(host: String, port: Int) = "wifi:$host:$port"
        fun usbId(serial: String) = "usb:$serial"
    }
}

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("adb_controller_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    fun loadRecentDevices(): List<RecentDevice> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                RecentDevice(
                    id = o.getString("id"),
                    type = runCatching { ConnectionType.valueOf(o.optString("type")) }
                        .getOrDefault(ConnectionType.WIFI),
                    host = o.optString("host"),
                    port = o.optInt("port", 5555),
                    model = o.optString("model"),
                    manufacturer = o.optString("manufacturer"),
                    androidVersion = o.optString("androidVersion"),
                    serial = o.optString("serial"),
                    nickname = o.optString("nickname"),
                    lastConnected = o.optLong("lastConnected"),
                    connectCount = o.optInt("connectCount", 1)
                )
            }.sortedByDescending { it.lastConnected }
        }.getOrDefault(emptyList())
    }

    fun saveRecentDevices(devices: List<RecentDevice>) {
        val array = JSONArray()
        devices.forEach { d ->
            array.put(
                JSONObject()
                    .put("id", d.id)
                    .put("type", d.type.name)
                    .put("host", d.host)
                    .put("port", d.port)
                    .put("model", d.model)
                    .put("manufacturer", d.manufacturer)
                    .put("androidVersion", d.androidVersion)
                    .put("serial", d.serial)
                    .put("nickname", d.nickname)
                    .put("lastConnected", d.lastConnected)
                    .put("connectCount", d.connectCount)
            )
        }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_RECENT = "recent_devices"
        const val MAX_RECENT = 20
    }
}
