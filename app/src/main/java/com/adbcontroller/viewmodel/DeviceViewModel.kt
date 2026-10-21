package com.adbcontroller.viewmodel

import android.app.Application
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adbcontroller.adb.AdbConnection
import com.adbcontroller.adb.AdbCrypto
import com.adbcontroller.adb.AdbService
import com.adbcontroller.adb.AdbServiceDiscovery
import com.adbcontroller.adb.PairingController
import com.adbcontroller.adb.WirelessDebugging
import com.adbcontroller.adb.AdbSync
import com.adbcontroller.data.AppPreferences
import com.adbcontroller.data.ConnectionType
import com.adbcontroller.data.RecentDevice
import com.adbcontroller.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DeviceInfo(
    val model: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val sdkVersion: String = "",
    val serialNumber: String = "",
    val buildId: String = "",
    val batteryLevel: String = "",
    val batteryStatus: String = "",
    val batteryTemp: String = "",
    val screenResolution: String = "",
    val ip: String = "",
    val totalRam: String = "",
    val availableRam: String = "",
    val ramUsedPercent: Int = -1,
    val totalStorage: String = "",
    val availableStorage: String = "",
    val storageUsedPercent: Int = -1,
    val cpuArch: String = "",
    val kernelVersion: String = "",
    val uptime: String = ""
)

data class PackageInfo(
    val packageName: String,
    val isSystemApp: Boolean = false,
    val isDisabled: Boolean = false
)

data class AppDetails(
    val packageName: String,
    val versionName: String = "",
    val versionCode: String = "",
    val targetSdk: String = "",
    val firstInstall: String = "",
    val lastUpdate: String = "",
    val apkPath: String = "",
    val isDisabled: Boolean = false
)

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val permissions: String = "",
    val date: String = "",
    val isLink: Boolean = false
)

data class LogEntry(
    val level: String,
    val tag: String,
    val message: String,
    val pid: String = "",
    val time: String = ""
)

data class TransferProgress(val label: String, val progress: Float? = null)

enum class QuickToggle {
    WIFI, BLUETOOTH, MOBILE_DATA, AIRPLANE, STAY_AWAKE, BATTERY_SAVER,
    DARK_MODE, SHOW_TOUCHES, POINTER_LOCATION, LAYOUT_BOUNDS, ANIMATIONS_OFF
}

data class QuickSettingsState(
    val loaded: Boolean = false,
    val toggles: Map<QuickToggle, Boolean> = emptyMap(),
    val brightness: Int = 128,
    val autoBrightness: Boolean = false,
    val fontScale: Float = 1f,
    val screenTimeoutMs: Long = 30_000,
    val physicalSize: String = "",
    val overrideSize: String = "",
    val physicalDensity: String = "",
    val overrideDensity: String = ""
)

enum class RootManager(val label: String) {
    MAGISK("Magisk"), KERNELSU("KernelSU"), APATCH("APatch"), UNKNOWN("Unknown su")
}

data class RootStatus(
    val checked: Boolean = false,
    val checking: Boolean = false,
    val hasSu: Boolean = false,
    val granted: Boolean = false,
    val manager: RootManager? = null,
    val managerVersion: String = "",
    val selinux: String = ""
)

data class RootModule(
    val id: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val pendingRemove: Boolean = false,
    val pendingUpdate: Boolean = false,
    val hasAction: Boolean = false
)

data class CpuCore(
    val id: Int,
    val online: Boolean,
    val governor: String,
    val curKhz: Long?,
    val minKhz: Long?,
    val maxKhz: Long?
)

data class PerformanceInfo(
    val cores: List<CpuCore> = emptyList(),
    val governors: List<String> = emptyList(),
    val thermal: List<Pair<String, Float>> = emptyList(),
    val swaps: String = "",
    val zramAlgorithm: String = "",
    val zramSizeBytes: Long? = null
)

data class AppPermission(val name: String, val granted: Boolean)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val target: String) : ConnectionState()
    data class Connected(val banner: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

sealed class PairingState {
    data object Idle : PairingState()
    data class Pairing(val step: String) : PairingState()
    data class Success(val message: String, val needsConnectPort: Boolean = false) : PairingState()
    data class Failed(val message: String) : PairingState()
}

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val crypto = AdbCrypto.get(application)
    private var connection: AdbConnection? = null
    private var currentDeviceId: String? = null

    val keyFingerprint: String = crypto.fingerprint()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedAddress = MutableStateFlow("")
    val connectedAddress: StateFlow<String> = _connectedAddress.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _packages = MutableStateFlow<List<PackageInfo>>(emptyList())
    val packages: StateFlow<List<PackageInfo>> = _packages.asStateFlow()

    private val _files = MutableStateFlow<List<FileEntry>>(emptyList())
    val files: StateFlow<List<FileEntry>> = _files.asStateFlow()

    private val _fileError = MutableStateFlow<String?>(null)
    val fileError: StateFlow<String?> = _fileError.asStateFlow()

    private val _currentPath = MutableStateFlow("/sdcard")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _shellOutput = MutableStateFlow("")
    val shellOutput: StateFlow<String> = _shellOutput.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _recentDevices = MutableStateFlow(prefs.loadRecentDevices())
    val recentDevices: StateFlow<List<RecentDevice>> = _recentDevices.asStateFlow()

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val wireless: WirelessDebugging
    private val discovery: AdbServiceDiscovery
    val nearbyServices: StateFlow<List<AdbService>>
    private var pairingJob: Job? = null

    init {
        PairingController.init(application)
        wireless = PairingController.wireless
        discovery = PairingController.discovery
        nearbyServices = discovery.services

        // When this phone is paired from the notification, finish by connecting to it.
        viewModelScope.launch {
            PairingController.selfPairing.filterNotNull().collect { event ->
                PairingController.consumeSelfPairing()
                when (event) {
                    is PairingController.SelfPairingEvent.Paired -> {
                        clearConnectionError()
                        pairingJob?.cancel()
                        pairingJob = launch(Dispatchers.IO) {
                            val port = findConnectPort(event.host)
                            if (port == null) {
                                _pairingState.value = PairingState.Success(
                                    "Paired. Enter the port shown under \"IP address & Port\" on the Wireless " +
                                        "debugging screen, then tap Connect.",
                                    needsConnectPort = true
                                )
                            } else {
                                connectPairedDevice(event.host, port)
                            }
                        }
                    }
                    is PairingController.SelfPairingEvent.Failed ->
                        _pairingState.value = PairingState.Failed(event.message)
                }
            }
        }
    }

    /** Starts the Shizuku-style flow for pairing this phone with itself via a notification. */
    fun startSelfPairing() {
        clearConnectionError()
        _pairingState.value = PairingState.Pairing(
            "Waiting for the pairing code. Enter it in the \"Pair this phone\" notification."
        )
        PairingController.showCodeNotification(getApplication())
    }

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.dynamicColor)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _transfer = MutableStateFlow<TransferProgress?>(null)
    val transfer: StateFlow<TransferProgress?> = _transfer.asStateFlow()

    private val _quickSettings = MutableStateFlow(QuickSettingsState())
    val quickSettings: StateFlow<QuickSettingsState> = _quickSettings.asStateFlow()

    private val _rootStatus = MutableStateFlow(RootStatus())
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    private val _modules = MutableStateFlow<List<RootModule>?>(null)
    val modules: StateFlow<List<RootModule>?> = _modules.asStateFlow()

    private val _moduleInstallLog = MutableStateFlow<String?>(null)
    val moduleInstallLog: StateFlow<String?> = _moduleInstallLog.asStateFlow()

    private val _rootOutput = MutableStateFlow<Pair<String, String>?>(null)
    val rootOutput: StateFlow<Pair<String, String>?> = _rootOutput.asStateFlow()

    private val _performance = MutableStateFlow<PerformanceInfo?>(null)
    val performance: StateFlow<PerformanceInfo?> = _performance.asStateFlow()

    private val _rootShell = MutableStateFlow(false)
    val rootShell: StateFlow<Boolean> = _rootShell.asStateFlow()

    private val _rootFileMode = MutableStateFlow(false)
    val rootFileMode: StateFlow<Boolean> = _rootFileMode.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val isWireless: Boolean get() = currentDeviceId?.startsWith("wifi:") == true

    private var logcatJob: Job? = null

    private fun notify(message: String) {
        _events.tryEmit(message)
    }

    // region Connection

    fun connectWireless(host: String, port: Int) {
        val target = host.trim()
        if (target.isBlank() || _connectionState.value is ConnectionState.Connecting) return
        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting("$target:$port")
            try {
                val conn = AdbConnection.connectTcp(target, port, crypto)
                attach(conn, RecentDevice.wifiId(target, port), "$target:$port")
                fetchDeviceInfo()
                recordRecentDevice(ConnectionType.WIFI, target, port)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(friendlyError(e, "$target:$port"))
            }
        }
    }

    fun connectRecent(device: RecentDevice) {
        if (device.type == ConnectionType.WIFI) {
            connectWireless(device.host, device.port)
        } else {
            notify("Plug in ${device.displayName} with a USB OTG cable to reconnect")
        }
    }

    fun connectUsb(device: UsbDevice) {
        if (_connectionState.value is ConnectionState.Connecting) return
        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting("USB: ${device.productName ?: device.deviceName}")
            try {
                val usbManager = getApplication<Application>()
                    .getSystemService(android.content.Context.USB_SERVICE) as UsbManager
                val conn = AdbConnection.connectUsb(usbManager, device, crypto)
                conn.connect()
                attach(conn, "usb:pending", "USB")
                fetchDeviceInfo()
                val serial = _deviceInfo.value.serialNumber.ifBlank { device.deviceName }
                currentDeviceId = RecentDevice.usbId(serial)
                recordRecentDevice(ConnectionType.USB, serial = serial)
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "USB connection failed")
            }
        }
    }

    private fun attach(conn: AdbConnection, deviceId: String, address: String) {
        connection?.let { old ->
            connection = null
            old.disconnect()
        }
        connection = conn
        currentDeviceId = deviceId
        _connectedAddress.value = address
        conn.onClosed = {
            if (connection === conn) {
                connection = null
                resetSessionState()
                _connectionState.value = ConnectionState.Error("Connection to $address was lost")
            }
        }
        _connectionState.value = ConnectionState.Connected(conn.deviceBanner)
    }

    fun disconnect() {
        val conn = connection
        connection = null
        conn?.disconnect()
        resetSessionState()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun clearConnectionError() {
        if (_connectionState.value is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun resetSessionState() {
        logcatJob?.cancel()
        currentDeviceId = null
        _connectedAddress.value = ""
        _deviceInfo.value = DeviceInfo()
        _packages.value = emptyList()
        _files.value = emptyList()
        _logEntries.value = emptyList()
        _shellOutput.value = ""
        _quickSettings.value = QuickSettingsState()
        _transfer.value = null
        _rootStatus.value = RootStatus()
        _modules.value = null
        _moduleInstallLog.value = null
        _rootOutput.value = null
        _performance.value = null
        _rootShell.value = false
        _rootFileMode.value = false
    }

    private fun friendlyError(e: Exception, target: String): String = when (e) {
        is java.net.ConnectException -> "Can't reach $target. Check the IP, the port, and that ADB over network is enabled."
        is java.net.SocketTimeoutException -> "Timed out connecting to $target. If the device shows an authorization prompt, allow it and retry."
        is java.net.UnknownHostException -> "Unknown host: $target"
        is java.net.NoRouteToHostException -> "No route to $target. Are both devices on the same network?"
        else -> e.message ?: "Connection failed"
    }

    // endregion

    // region Recent devices

    private fun recordRecentDevice(
        type: ConnectionType,
        host: String = "",
        port: Int = 5555,
        serial: String = ""
    ) {
        val info = _deviceInfo.value
        val id = if (type == ConnectionType.WIFI) RecentDevice.wifiId(host, port) else RecentDevice.usbId(serial)
        val existing = _recentDevices.value.firstOrNull { it.id == id }
        val updated = RecentDevice(
            id = id,
            type = type,
            host = host,
            port = port,
            model = info.model.ifBlank { existing?.model.orEmpty() },
            manufacturer = info.manufacturer.ifBlank { existing?.manufacturer.orEmpty() },
            androidVersion = info.androidVersion.ifBlank { existing?.androidVersion.orEmpty() },
            serial = info.serialNumber.ifBlank { serial },
            nickname = existing?.nickname.orEmpty(),
            lastConnected = System.currentTimeMillis(),
            connectCount = (existing?.connectCount ?: 0) + 1
        )
        val list = listOf(updated) + _recentDevices.value.filter { it.id != id }
        saveRecent(list.take(AppPreferences.MAX_RECENT))
    }

    fun renameRecentDevice(id: String, nickname: String) {
        saveRecent(_recentDevices.value.map { if (it.id == id) it.copy(nickname = nickname.trim()) else it })
    }

    fun removeRecentDevice(id: String) {
        saveRecent(_recentDevices.value.filter { it.id != id })
    }

    fun clearRecentDevices() {
        saveRecent(emptyList())
        notify("Recent devices cleared")
    }

    private fun saveRecent(list: List<RecentDevice>) {
        _recentDevices.value = list
        prefs.saveRecentDevices(list)
    }

    // endregion

    // region Appearance

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.dynamicColor = enabled
        _dynamicColor.value = enabled
    }

    // endregion

    // region Device info

    private suspend fun fetchDeviceInfo() {
        val conn = connection ?: return
        val commands = listOf(
            "getprop ro.product.model",
            "getprop ro.product.manufacturer",
            "getprop ro.build.version.release",
            "getprop ro.build.version.sdk",
            "getprop ro.serialno",
            "getprop ro.build.display.id",
            "cat /sys/class/power_supply/battery/capacity",
            "cat /sys/class/power_supply/battery/status",
            "cat /sys/class/power_supply/battery/temp",
            "wm size",
            "ip route",
            "cat /proc/meminfo",
            "df /data",
            "getprop ro.product.cpu.abi",
            "uname -r",
            "cat /proc/uptime"
        )
        try {
            val output = conn.shellCommand(commands.joinToString("; echo '$SEP'; "), 20_000)
            val p = output.split(SEP).map { it.trim() }
            fun part(i: Int) = p.getOrElse(i) { "" }

            val memInfo = part(11)
            val memTotalKb = Regex("MemTotal:\\s+(\\d+)").find(memInfo)?.groupValues?.get(1)?.toLongOrNull()
            val memAvailKb = Regex("MemAvailable:\\s+(\\d+)").find(memInfo)?.groupValues?.get(1)?.toLongOrNull()

            val dfParts = part(12).lines().lastOrNull().orEmpty().trim().split(Regex("\\s+"))
            val storageTotalKb = dfParts.getOrNull(1)?.toLongOrNull()
            val storageAvailKb = dfParts.getOrNull(3)?.toLongOrNull()

            val tempTenths = part(8).toIntOrNull()
            val uptimeSec = part(15).substringBefore(" ").toDoubleOrNull()?.toLong()

            _deviceInfo.value = DeviceInfo(
                model = part(0),
                manufacturer = part(1),
                androidVersion = part(2),
                sdkVersion = part(3),
                serialNumber = part(4),
                buildId = part(5),
                batteryLevel = part(6),
                batteryStatus = part(7),
                batteryTemp = tempTenths?.let { "%.1f °C".format(it / 10.0) } ?: "",
                screenResolution = part(9).lines().firstOrNull { it.contains("Physical size") }
                    ?.substringAfter(": ")?.trim().orEmpty(),
                ip = Regex("src (\\S+)").find(part(10))?.groupValues?.get(1).orEmpty(),
                totalRam = memTotalKb?.let { formatBytes(it * 1024) }.orEmpty(),
                availableRam = memAvailKb?.let { formatBytes(it * 1024) }.orEmpty(),
                ramUsedPercent = if (memTotalKb != null && memAvailKb != null && memTotalKb > 0)
                    ((memTotalKb - memAvailKb) * 100 / memTotalKb).toInt() else -1,
                totalStorage = storageTotalKb?.let { formatBytes(it * 1024) }.orEmpty(),
                availableStorage = storageAvailKb?.let { formatBytes(it * 1024) }.orEmpty(),
                storageUsedPercent = if (storageTotalKb != null && storageAvailKb != null && storageTotalKb > 0)
                    ((storageTotalKb - storageAvailKb) * 100 / storageTotalKb).toInt() else -1,
                cpuArch = part(13),
                kernelVersion = part(14),
                uptime = uptimeSec?.let { formatUptime(it) }.orEmpty()
            )
        } catch (_: Exception) {
        }
    }

    fun refreshDeviceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            fetchDeviceInfo()
            _isLoading.value = false
        }
    }

    // endregion

    // region Shell

    fun executeShellCommand(command: String) {
        val asRoot = _rootShell.value
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val prompt = if (asRoot) "#" else "$"
            try {
                val result = if (asRoot) conn.shellCommand(suCommand(command), 30_000)
                else conn.shellCommand(command)
                _shellOutput.value += "$prompt $command\n$result\n"
            } catch (e: Exception) {
                _shellOutput.value += "$prompt $command\nError: ${e.message}\n"
            }
        }
    }

    fun setRootShell(enabled: Boolean) {
        _rootShell.value = enabled && _rootStatus.value.granted
    }

    fun clearShellOutput() {
        _shellOutput.value = ""
    }

    private fun runCommand(command: String, successMessage: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            try {
                val out = conn.shellCommand(command).trim()
                if (successMessage != null) notify(successMessage)
                else if (out.isNotBlank() && out.contains("Error", ignoreCase = true)) notify(out.lines().first())
            } catch (e: Exception) {
                notify("Command failed: ${e.message}")
            }
        }
    }

    // endregion

    // region Remote control

    suspend fun captureScreen(): ByteArray? = withContext(Dispatchers.IO) {
        val conn = connection ?: return@withContext null
        try {
            conn.execBytes("screencap -p").takeIf { it.size > 8 }
        } catch (_: Exception) {
            null
        }
    }

    fun sendKey(keyCode: Int) = runCommand("input keyevent $keyCode")

    fun tap(x: Int, y: Int) = runCommand("input tap $x $y")

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) =
        runCommand("input swipe $x1 $y1 $x2 $y2 $durationMs")

    fun longPress(x: Int, y: Int) = runCommand("input swipe $x $y $x $y 800")

    fun inputText(text: String) {
        if (text.isEmpty()) return
        runCommand("input text ${shellQuote(text.replace(" ", "%s"))}")
    }

    fun openUrl(url: String) {
        val target = if (url.contains("://")) url else "https://$url"
        runCommand("am start -a android.intent.action.VIEW -d ${shellQuote(target)}", "Opened $target")
    }

    fun expandNotifications() = runCommand("cmd statusbar expand-notifications")

    fun expandQuickSettingsPanel() = runCommand("cmd statusbar expand-settings")

    fun collapseStatusBar() = runCommand("cmd statusbar collapse")

    // endregion

    // region Packages

    fun loadPackages(showSystem: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            _isLoading.value = true
            try {
                val thirdParty = conn.shellCommand("pm list packages -3")
                val system = if (showSystem) conn.shellCommand("pm list packages -s") else ""
                val disabled = conn.shellCommand("pm list packages -d").lines()
                    .map { it.removePrefix("package:").trim() }.toSet()

                fun parse(raw: String, isSystem: Boolean) = raw.lines()
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotBlank() }
                    .map { PackageInfo(it, isSystem, it in disabled) }

                _packages.value = (parse(thirdParty, false) + parse(system, true))
                    .sortedBy { it.packageName }
            } catch (e: Exception) {
                notify("Failed to load apps: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    fun getAppDetails(packageName: String, onResult: (AppDetails) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val pkg = shellQuote(packageName)
            try {
                val dump = conn.shellCommand(
                    "dumpsys package $pkg | grep -E 'versionName=|versionCode=|firstInstallTime=|lastUpdateTime='; " +
                        "echo '$SEP'; pm path $pkg; echo '$SEP'; pm list packages -d"
                )
                val parts = dump.split(SEP)
                val info = parts.getOrElse(0) { "" }
                val details = AppDetails(
                    packageName = packageName,
                    versionName = Regex("versionName=(\\S+)").find(info)?.groupValues?.get(1).orEmpty(),
                    versionCode = Regex("versionCode=(\\d+)").find(info)?.groupValues?.get(1).orEmpty(),
                    targetSdk = Regex("targetSdk=(\\d+)").find(info)?.groupValues?.get(1).orEmpty(),
                    firstInstall = Regex("firstInstallTime=(.+)").find(info)?.groupValues?.get(1)?.trim().orEmpty(),
                    lastUpdate = Regex("lastUpdateTime=(.+)").find(info)?.groupValues?.get(1)?.trim().orEmpty(),
                    apkPath = parts.getOrElse(1) { "" }.lines()
                        .firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim().orEmpty(),
                    isDisabled = parts.getOrElse(2) { "" }.lines().any { it.trim() == "package:$packageName" }
                )
                withContext(Dispatchers.Main) { onResult(details) }
            } catch (e: Exception) {
                notify("Failed to read app details: ${e.message}")
            }
        }
    }

    fun launchApp(packageName: String) = runCommand(
        "monkey -p ${shellQuote(packageName)} -c android.intent.category.LAUNCHER 1",
        "Launched $packageName"
    )

    fun openAppSettingsOnDevice(packageName: String) = runCommand(
        "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:$packageName",
        "Opened app info on device"
    )

    fun setAppEnabled(packageName: String, enabled: Boolean, showSystem: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val cmd = if (enabled) "pm enable" else "pm disable-user --user 0"
            val out = conn.shellCommand("$cmd ${shellQuote(packageName)}")
            notify(if (out.contains("new state", ignoreCase = true)) {
                if (enabled) "Enabled $packageName" else "Disabled $packageName"
            } else out.trim().ifBlank { "Done" })
            loadPackages(showSystem)
        }
    }

    fun uninstallPackage(packageName: String, isSystem: Boolean = false, showSystem: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val cmd = if (isSystem) "pm uninstall --user 0" else "pm uninstall"
            val out = conn.shellCommand("$cmd ${shellQuote(packageName)}").trim()
            notify(if (out.contains("Success")) "Uninstalled $packageName" else "Uninstall failed: $out")
            loadPackages(showSystem)
        }
    }

    fun forceStopPackage(packageName: String) =
        runCommand("am force-stop ${shellQuote(packageName)}", "Force stopped $packageName")

    fun clearPackageData(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val out = conn.shellCommand("pm clear ${shellQuote(packageName)}").trim()
            notify(if (out.contains("Success")) "Cleared data for $packageName" else "Clear failed: $out")
        }
    }

    fun installApk(uri: Uri, showSystem: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val resolver = getApplication<Application>().contentResolver
            val (name, size) = queryNameAndSize(uri)
            val remote = "/data/local/tmp/adbcontroller_install.apk"
            try {
                _transfer.value = TransferProgress("Uploading $name", 0f)
                val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open file")
                input.use { stream ->
                    AdbSync.use(conn) { sync ->
                        sync.push(stream, remote) { sent ->
                            _transfer.value = TransferProgress("Uploading $name", progressOf(sent, size))
                        }
                    }
                }
                _transfer.value = TransferProgress("Installing $name")
                val out = conn.shellCommand("pm install -r -t $remote; rm -f $remote", 180_000).trim()
                notify(if (out.contains("Success")) "Installed $name" else "Install failed: ${out.lines().lastOrNull().orEmpty()}")
                if (_packages.value.isNotEmpty()) loadPackages(showSystem)
            } catch (e: Exception) {
                notify("Install failed: ${e.message}")
            } finally {
                _transfer.value = null
            }
        }
    }

    fun extractApk(packageName: String, destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val path = conn.shellCommand("pm path ${shellQuote(packageName)}").lines()
                .firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim()
            if (path.isNullOrBlank()) {
                notify("Could not find APK for $packageName")
                return@launch
            }
            pullToUri(conn, path, destination, "$packageName.apk")
        }
    }

    // endregion

    // region Files

    fun listFiles(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            _isLoading.value = true
            _currentPath.value = path
            _fileError.value = null
            try {
                val target = if (path.endsWith("/")) path else "$path/"
                val result = conn.shellCommand(fsCommand("ls -la ${shellQuote(target)}"))
                val entries = result.lines().mapNotNull { parseLsLine(it, path) }
                _files.value = entries.sortedWith(
                    compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
                )
                if (entries.isEmpty() && result.contains("Permission denied")) {
                    _fileError.value = "Permission denied"
                }
            } catch (e: Exception) {
                _files.value = emptyList()
                _fileError.value = e.message ?: "Failed to list directory"
            }
            _isLoading.value = false
        }
    }

    fun navigateUp() {
        val parent = _currentPath.value.trimEnd('/').substringBeforeLast("/", "/")
        listFiles(parent.ifEmpty { "/" })
    }

    fun createFolder(name: String) {
        val dir = _currentPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val out = conn.shellCommand(fsCommand("mkdir -p ${shellQuote(joinPath(dir, name))}")).trim()
            if (out.isNotBlank()) notify(out) else notify("Created $name")
            listFiles(dir)
        }
    }

    fun deleteFile(entry: FileEntry) {
        val dir = _currentPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val out = conn.shellCommand(fsCommand("rm -rf ${shellQuote(entry.path)}")).trim()
            notify(if (out.isBlank()) "Deleted ${entry.name}" else out)
            listFiles(dir)
        }
    }

    fun renameFile(entry: FileEntry, newName: String) {
        val dir = _currentPath.value
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val out = conn.shellCommand(
                fsCommand("mv ${shellQuote(entry.path)} ${shellQuote(joinPath(dir, newName))}")
            ).trim()
            notify(if (out.isBlank()) "Renamed to $newName" else out)
            listFiles(dir)
        }
    }

    fun downloadFile(remotePath: String, destination: Uri) {
        val asRoot = _rootFileMode.value
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val label = remotePath.substringAfterLast('/')
            if (!asRoot) {
                pullToUri(conn, remotePath, destination, label)
                return@launch
            }
            val staging = ROOT_STAGING_FILE
            val out = conn.shellCommand(
                suCommand("cp ${shellQuote(remotePath)} $staging && chmod 644 $staging"), 120_000
            ).trim()
            if (out.isNotBlank()) {
                notify("Copy failed: ${out.lines().first()}")
                return@launch
            }
            pullToUri(conn, staging, destination, label)
            conn.shellCommand(suCommand("rm -f $staging"))
        }
    }

    fun uploadFile(uri: Uri) {
        val dir = _currentPath.value
        val asRoot = _rootFileMode.value
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val resolver = getApplication<Application>().contentResolver
            val (name, size) = queryNameAndSize(uri)
            val destination = joinPath(dir, name)
            try {
                _transfer.value = TransferProgress("Uploading $name", 0f)
                val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open file")
                input.use { stream ->
                    AdbSync.use(conn) { sync ->
                        sync.push(stream, if (asRoot) ROOT_STAGING_FILE else destination) { sent ->
                            _transfer.value = TransferProgress("Uploading $name", progressOf(sent, size))
                        }
                    }
                }
                if (asRoot) {
                    val out = conn.shellCommand(
                        suCommand("mv $ROOT_STAGING_FILE ${shellQuote(destination)}"), 120_000
                    ).trim()
                    if (out.isNotBlank()) throw IllegalStateException(out.lines().first())
                }
                notify("Uploaded $name")
                listFiles(dir)
            } catch (e: Exception) {
                notify("Upload failed: ${e.message}")
            } finally {
                _transfer.value = null
            }
        }
    }

    fun installRemoteApk(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            _transfer.value = TransferProgress("Installing ${path.substringAfterLast('/')}")
            try {
                val out = conn.shellCommand("pm install -r -t ${shellQuote(path)}", 180_000).trim()
                notify(if (out.contains("Success")) "Installed successfully" else "Install failed: ${out.lines().lastOrNull().orEmpty()}")
            } finally {
                _transfer.value = null
            }
        }
    }

    private suspend fun pullToUri(conn: AdbConnection, remotePath: String, destination: Uri, label: String) {
        val resolver = getApplication<Application>().contentResolver
        try {
            _transfer.value = TransferProgress("Downloading $label", 0f)
            val output = resolver.openOutputStream(destination) ?: throw IllegalStateException("Cannot write file")
            output.use { out ->
                AdbSync.use(conn) { sync ->
                    val size = runCatching { sync.statSize(remotePath) }.getOrDefault(-1L)
                    sync.pull(remotePath, out) { received ->
                        _transfer.value = TransferProgress("Downloading $label", progressOf(received, size))
                    }
                }
            }
            notify("Saved $label")
        } catch (e: Exception) {
            notify("Download failed: ${e.message}")
        } finally {
            _transfer.value = null
        }
    }

    // endregion

    // region Logcat

    fun startLogcat(filter: String = "") {
        logcatJob?.cancel()
        _logEntries.value = emptyList()

        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            try {
                val cmd = if (filter.isNotBlank()) "logcat -v threadtime $filter" else "logcat -v threadtime"
                val stream = conn.open("shell:$cmd")
                val pending = StringBuilder()
                try {
                    while (isActive && !stream.isClosed) {
                        pending.append(stream.readString())
                        val text = pending.toString()
                        val lastNewline = text.lastIndexOf('\n')
                        if (lastNewline < 0) continue
                        pending.setLength(0)
                        pending.append(text.substring(lastNewline + 1))
                        val newEntries = text.substring(0, lastNewline).lines().mapNotNull { parseLogLine(it) }
                        if (newEntries.isNotEmpty()) {
                            val combined = _logEntries.value + newEntries
                            _logEntries.value = if (combined.size > 2000) combined.takeLast(1500) else combined
                        }
                    }
                } finally {
                    stream.close()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopLogcat() {
        logcatJob?.cancel()
        logcatJob = null
    }

    fun clearLogcat() {
        _logEntries.value = emptyList()
    }

    private fun parseLogLine(line: String): LogEntry? {
        val match = LOG_REGEX.find(line) ?: return null
        return LogEntry(
            time = "${match.groupValues[1]} ${match.groupValues[2]}",
            pid = match.groupValues[3],
            level = match.groupValues[5],
            tag = match.groupValues[6].trim(),
            message = match.groupValues[7]
        )
    }

    // endregion

    // region System

    fun takeScreenshot(onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            val data = captureScreen()
            onResult(data)
        }
    }

    fun saveBytes(bytes: ByteArray, destination: Uri, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                notify("Saved $label")
            } catch (e: Exception) {
                notify("Save failed: ${e.message}")
            }
        }
    }

    fun reboot(mode: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val cmd = when (mode) {
                "" -> "reboot"
                "poweroff" -> "reboot -p"
                else -> "reboot $mode"
            }
            try {
                conn.shellCommand(cmd, 3_000)
            } catch (_: Exception) {
            }
            disconnect()
            notify(if (mode == "poweroff") "Device is powering off" else "Device is rebooting")
        }
    }

    fun getRunningProcesses(onResult: (String) -> Unit) = runForResult("ps -A -o PID,USER,RSS,NAME | head -200", onResult)

    fun getBatteryInfo(onResult: (String) -> Unit) = runForResult("dumpsys battery", onResult)

    fun getNetworkInfo(onResult: (String) -> Unit) =
        runForResult("ip addr show && echo '---' && ip route", onResult)

    fun getProperties(onResult: (List<Pair<String, String>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val props = conn.shellCommand("getprop").lines().mapNotNull { line ->
                val m = Regex("^\\[(.*?)]: \\[(.*)]$").find(line.trim()) ?: return@mapNotNull null
                m.groupValues[1] to m.groupValues[2]
            }
            withContext(Dispatchers.Main) { onResult(props) }
        }
    }

    private fun runForResult(command: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val result = try {
                conn.shellCommand(command)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun enableWifiAdb(port: Int = 5555, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val result = try {
                conn.service("tcpip:$port").trim().ifBlank { "restarting in TCP mode port: $port" }
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun switchToWifi(port: Int = 5555) {
        val ip = _deviceInfo.value.ip
        if (ip.isBlank()) {
            notify("Device IP address is unknown. Is the device on Wi-Fi?")
            return
        }
        enableWifiAdb(port) {
            viewModelScope.launch {
                disconnect()
                delay(2500)
                connectWireless(ip, port)
            }
        }
    }

    // endregion

    // region Quick settings

    fun loadQuickSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val commands = listOf(
                "settings get global wifi_on",
                "settings get global bluetooth_on",
                "settings get global mobile_data",
                "settings get global airplane_mode_on",
                "settings get global stay_on_while_plugged_in",
                "settings get global low_power",
                "cmd uimode night",
                "settings get system show_touches",
                "settings get system pointer_location",
                "getprop debug.layout",
                "settings get global window_animation_scale",
                "settings get system screen_brightness",
                "settings get system screen_brightness_mode",
                "settings get system font_scale",
                "settings get system screen_off_timeout",
                "wm size",
                "wm density"
            )
            try {
                val p = conn.shellCommand(commands.joinToString("; echo '$SEP'; ")).split(SEP).map { it.trim() }
                fun part(i: Int) = p.getOrElse(i) { "" }
                fun on(i: Int) = part(i).toIntOrNull()?.let { it != 0 } ?: false

                val size = part(15)
                val density = part(16)
                _quickSettings.value = QuickSettingsState(
                    loaded = true,
                    toggles = mapOf(
                        QuickToggle.WIFI to on(0),
                        QuickToggle.BLUETOOTH to on(1),
                        QuickToggle.MOBILE_DATA to on(2),
                        QuickToggle.AIRPLANE to on(3),
                        QuickToggle.STAY_AWAKE to on(4),
                        QuickToggle.BATTERY_SAVER to on(5),
                        QuickToggle.DARK_MODE to part(6).contains("yes", ignoreCase = true),
                        QuickToggle.SHOW_TOUCHES to on(7),
                        QuickToggle.POINTER_LOCATION to on(8),
                        QuickToggle.LAYOUT_BOUNDS to (part(9) == "true"),
                        QuickToggle.ANIMATIONS_OFF to (part(10).toFloatOrNull() == 0f)
                    ),
                    brightness = part(11).toIntOrNull() ?: 128,
                    autoBrightness = part(12) == "1",
                    fontScale = part(13).toFloatOrNull() ?: 1f,
                    screenTimeoutMs = part(14).toLongOrNull() ?: 30_000,
                    physicalSize = Regex("Physical size: (\\S+)").find(size)?.groupValues?.get(1).orEmpty(),
                    overrideSize = Regex("Override size: (\\S+)").find(size)?.groupValues?.get(1).orEmpty(),
                    physicalDensity = Regex("Physical density: (\\d+)").find(density)?.groupValues?.get(1).orEmpty(),
                    overrideDensity = Regex("Override density: (\\d+)").find(density)?.groupValues?.get(1).orEmpty()
                )
            } catch (e: Exception) {
                notify("Failed to read settings: ${e.message}")
            }
        }
    }

    fun setToggle(toggle: QuickToggle, enabled: Boolean) {
        val onOff = if (enabled) "enable" else "disable"
        val bit = if (enabled) "1" else "0"
        val command = when (toggle) {
            QuickToggle.WIFI -> "svc wifi $onOff"
            QuickToggle.BLUETOOTH -> "cmd bluetooth_manager $onOff || svc bluetooth $onOff"
            QuickToggle.MOBILE_DATA -> "svc data $onOff"
            QuickToggle.AIRPLANE -> "cmd connectivity airplane-mode $onOff"
            QuickToggle.STAY_AWAKE -> "svc power stayon ${if (enabled) "true" else "false"}"
            QuickToggle.BATTERY_SAVER -> "cmd power set-mode $bit || settings put global low_power $bit"
            QuickToggle.DARK_MODE -> "cmd uimode night ${if (enabled) "yes" else "no"}"
            QuickToggle.SHOW_TOUCHES -> "settings put system show_touches $bit"
            QuickToggle.POINTER_LOCATION -> "settings put system pointer_location $bit"
            QuickToggle.LAYOUT_BOUNDS -> "setprop debug.layout ${enabled}; service call activity 1599295570 > /dev/null 2>&1"
            QuickToggle.ANIMATIONS_OFF -> {
                val scale = if (enabled) "0" else "1"
                "settings put global window_animation_scale $scale; " +
                    "settings put global transition_animation_scale $scale; " +
                    "settings put global animator_duration_scale $scale"
            }
        }
        _quickSettings.value = _quickSettings.value.let { it.copy(toggles = it.toggles + (toggle to enabled)) }
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            try {
                conn.shellCommand(command)
                delay(800)
                loadQuickSettings()
            } catch (e: Exception) {
                notify("Failed: ${e.message}")
            }
        }
    }

    fun setBrightness(value: Int) {
        _quickSettings.value = _quickSettings.value.copy(brightness = value, autoBrightness = false)
        runCommand("settings put system screen_brightness_mode 0; settings put system screen_brightness $value")
    }

    fun setAutoBrightness(enabled: Boolean) {
        _quickSettings.value = _quickSettings.value.copy(autoBrightness = enabled)
        runCommand("settings put system screen_brightness_mode ${if (enabled) 1 else 0}")
    }

    fun setFontScale(scale: Float) {
        _quickSettings.value = _quickSettings.value.copy(fontScale = scale)
        runCommand("settings put system font_scale $scale", "Font scale set to ${(scale * 100).toInt()}%")
    }

    fun setScreenTimeout(ms: Long) {
        _quickSettings.value = _quickSettings.value.copy(screenTimeoutMs = ms)
        runCommand("settings put system screen_off_timeout $ms", "Screen timeout updated")
    }

    fun setDensity(dpi: Int?) {
        val cmd = if (dpi == null) "wm density reset" else "wm density $dpi"
        viewModelScope.launch(Dispatchers.IO) {
            connection?.shellCommand(cmd)
            notify(if (dpi == null) "Density reset" else "Density set to $dpi")
            loadQuickSettings()
        }
    }

    fun setResolution(size: String?) {
        val cmd = if (size == null) "wm size reset" else "wm size $size"
        viewModelScope.launch(Dispatchers.IO) {
            connection?.shellCommand(cmd)
            notify(if (size == null) "Resolution reset" else "Resolution set to $size")
            loadQuickSettings()
        }
    }

    // endregion

    // region Root

    private fun suCommand(command: String) = "su -c ${shellQuote(command)}"

    private fun fsCommand(command: String) = if (_rootFileMode.value) suCommand(command) else command

    fun setRootFileMode(enabled: Boolean) {
        _rootFileMode.value = enabled && _rootStatus.value.granted
        listFiles(_currentPath.value)
    }

    /** Asks su for uid 0; the target may show a superuser prompt that the user must approve. */
    fun checkRoot() {
        if (_rootStatus.value.checking) return
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            _rootStatus.value = _rootStatus.value.copy(checking = true)
            try {
                val probe = conn.shellCommand("command -v su; echo '$SEP'; getenforce").split(SEP)
                val hasSu = probe.getOrElse(0) { "" }.trim().isNotBlank()
                val selinux = probe.getOrElse(1) { "" }.trim()
                if (!hasSu) {
                    _rootStatus.value = RootStatus(checked = true, selinux = selinux)
                    return@launch
                }
                val granted = conn.shellCommand("su -c id", 45_000).contains("uid=0")
                var manager: RootManager? = null
                var version = ""
                if (granted) {
                    val p = conn.shellCommand(
                        suCommand(
                            "magisk -v 2>/dev/null; echo '$SEP'; magisk -V 2>/dev/null; echo '$SEP'; " +
                                "/data/adb/ksud -V 2>/dev/null; echo '$SEP'; /data/adb/apd -V 2>/dev/null"
                        )
                    ).split(SEP).map { it.trim() }
                    val magisk = p.getOrElse(0) { "" }
                    val ksu = p.getOrElse(2) { "" }
                    val apd = p.getOrElse(3) { "" }
                    when {
                        magisk.contains("MAGISK", ignoreCase = true) -> {
                            manager = RootManager.MAGISK
                            version = magisk.substringBefore(":") +
                                p.getOrElse(1) { "" }.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        }
                        ksu.isNotBlank() -> {
                            manager = RootManager.KERNELSU
                            version = ksu.removePrefix("ksud").trim()
                        }
                        apd.isNotBlank() -> {
                            manager = RootManager.APATCH
                            version = apd.removePrefix("apd").trim()
                        }
                        else -> manager = RootManager.UNKNOWN
                    }
                }
                _rootStatus.value = RootStatus(
                    checked = true,
                    hasSu = true,
                    granted = granted,
                    manager = manager,
                    managerVersion = version,
                    selinux = selinux
                )
                if (granted) loadModules()
            } catch (e: Exception) {
                _rootStatus.value = RootStatus(checked = true)
                notify("Root check failed: ${e.message}")
            }
        }
    }

    fun loadModules() {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            if (!_rootStatus.value.granted) return@launch
            val script = "for d in /data/adb/modules/*/; do " +
                "[ -f \"\$d/module.prop\" ] || continue; " +
                "echo \"@@MOD \$(basename \$d)\"; cat \"\$d/module.prop\"; echo; " +
                "[ -f \"\$d/disable\" ] && echo __disabled=1; " +
                "[ -f \"\$d/remove\" ] && echo __remove=1; " +
                "[ -f \"\$d/update\" ] && echo __update=1; " +
                "[ -f \"\$d/action.sh\" ] && echo __action=1; " +
                "done"
            try {
                val out = conn.shellCommand(suCommand(script), 30_000)
                _modules.value = out.split("@@MOD ").drop(1).mapNotNull { block ->
                    val lines = block.lines()
                    val dirId = lines.firstOrNull()?.trim().orEmpty()
                    if (dirId.isBlank()) return@mapNotNull null
                    val props = lines.drop(1).mapNotNull { line ->
                        val idx = line.indexOf('=')
                        if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                    }.toMap()
                    RootModule(
                        id = dirId,
                        name = props["name"].orEmpty().ifBlank { dirId },
                        version = props["version"].orEmpty(),
                        author = props["author"].orEmpty(),
                        description = props["description"].orEmpty(),
                        enabled = props["__disabled"] == null,
                        pendingRemove = props["__remove"] != null,
                        pendingUpdate = props["__update"] != null,
                        hasAction = props["__action"] != null
                    )
                }.sortedBy { it.name.lowercase() }
            } catch (e: Exception) {
                notify("Failed to read modules: ${e.message}")
            }
        }
    }

    fun setModuleEnabled(module: RootModule, enabled: Boolean) =
        moduleFlag(module, "disable", set = !enabled, if (enabled) "Enabled" else "Disabled")

    fun setModuleRemoval(module: RootModule, remove: Boolean) =
        moduleFlag(module, "remove", set = remove, if (remove) "Marked for removal:" else "Restored")

    private fun moduleFlag(module: RootModule, flag: String, set: Boolean, verb: String) {
        if (!MODULE_ID_REGEX.matches(module.id)) {
            notify("Invalid module id")
            return
        }
        val path = "/data/adb/modules/${module.id}/$flag"
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            conn.shellCommand(suCommand(if (set) "touch $path" else "rm -f $path"))
            notify("$verb ${module.name} — reboot to apply")
            loadModules()
        }
    }

    fun installModule(uri: Uri) {
        val manager = _rootStatus.value.manager
        val installCmd = when (manager) {
            RootManager.MAGISK -> "magisk --install-module"
            RootManager.KERNELSU -> "/data/adb/ksud module install"
            RootManager.APATCH -> "/data/adb/apd module install"
            else -> {
                notify("Module install needs Magisk, KernelSU or APatch")
                return
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val (name, size) = queryNameAndSize(uri)
            val remote = "/data/local/tmp/adbcontroller_module.zip"
            try {
                _transfer.value = TransferProgress("Uploading $name", 0f)
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file")
                input.use { stream ->
                    AdbSync.use(conn) { sync ->
                        sync.push(stream, remote) { sent ->
                            _transfer.value = TransferProgress("Uploading $name", progressOf(sent, size))
                        }
                    }
                }
                _transfer.value = TransferProgress("Installing $name")
                val log = conn.shellCommand(suCommand("$installCmd $remote 2>&1; rm -f $remote"), 300_000)
                _moduleInstallLog.value = log.trim().ifBlank { "Installer finished with no output." }
                loadModules()
            } catch (e: Exception) {
                notify("Module install failed: ${e.message}")
            } finally {
                _transfer.value = null
            }
        }
    }

    fun dismissModuleLog() {
        _moduleInstallLog.value = null
    }

    fun getKernelLog(onResult: (String) -> Unit) {
        val cmd = if (_rootStatus.value.granted) suCommand("dmesg | tail -n 1000") else "dmesg | tail -n 1000"
        runForResult(cmd) { out ->
            onResult(out.ifBlank { "No output. Reading the kernel log usually requires root." })
        }
    }

    fun softReboot() {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            try {
                conn.shellCommand(suCommand("setprop ctl.restart zygote"), 3_000)
            } catch (_: Exception) {
            }
            notify("Restarting Android framework")
        }
    }

    fun restartSystemUi() {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            conn.shellCommand(suCommand("pkill -f com.android.systemui || killall com.android.systemui"))
            notify("SystemUI restarted")
        }
    }

    fun setAllModulesEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val cmd = if (enabled) "rm -f /data/adb/modules/*/disable"
            else "for d in /data/adb/modules/*/; do touch \"\$d/disable\"; done"
            conn.shellCommand(suCommand(cmd))
            notify(if (enabled) "All modules enabled — reboot to apply" else "All modules disabled — reboot to apply")
            loadModules()
        }
    }

    fun runModuleAction(module: RootModule) {
        if (!MODULE_ID_REGEX.matches(module.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            _transfer.value = TransferProgress("Running ${module.name} action")
            try {
                val dir = "/data/adb/modules/${module.id}"
                val out = conn.shellCommand(suCommand("cd $dir && sh ./action.sh 2>&1"), 120_000)
                _rootOutput.value = "${module.name} action" to out.trim().ifBlank { "Finished with no output." }
            } finally {
                _transfer.value = null
            }
        }
    }

    fun showMagiskLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val out = conn.shellCommand(
                suCommand("tail -n 500 /cache/magisk.log 2>/dev/null || tail -n 500 /data/adb/magisk.log 2>/dev/null")
            )
            _rootOutput.value = "Magisk log" to out.trim().ifBlank { "No Magisk log found." }
        }
    }

    fun dismissRootOutput() {
        _rootOutput.value = null
    }

    fun loadPerformance() {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val script = "rd(){ cat \"\$1\" 2>/dev/null || echo -; }; " +
                "for c in /sys/devices/system/cpu/cpu[0-9]*; do " +
                "f=\$c/cpufreq; echo \"@@CPU \${c##*cpu} \$(rd \$c/online) \$(rd \$f/scaling_governor) " +
                "\$(rd \$f/scaling_cur_freq) \$(rd \$f/scaling_min_freq) \$(rd \$f/scaling_max_freq)\"; done; " +
                "echo '$SEP'; rd /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors; " +
                "echo '$SEP'; for z in /sys/class/thermal/thermal_zone*; do echo \"\$(rd \$z/type)=\$(rd \$z/temp)\"; done; " +
                "echo '$SEP'; cat /proc/swaps; " +
                "echo '$SEP'; rd /sys/block/zram0/comp_algorithm; " +
                "echo '$SEP'; rd /sys/block/zram0/disksize"
            try {
                val cmd = if (_rootStatus.value.granted) suCommand(script) else script
                val p = conn.shellCommand(cmd).split(SEP)
                val cores = p.getOrElse(0) { "" }.lines().filter { it.startsWith("@@CPU") }.mapNotNull { line ->
                    val f = line.removePrefix("@@CPU").trim().split(Regex("\\s+"))
                    val id = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    CpuCore(
                        id = id,
                        online = f.getOrNull(1) != "0",
                        governor = f.getOrNull(2)?.takeIf { it != "-" }.orEmpty(),
                        curKhz = f.getOrNull(3)?.toLongOrNull(),
                        minKhz = f.getOrNull(4)?.toLongOrNull(),
                        maxKhz = f.getOrNull(5)?.toLongOrNull()
                    )
                }.sortedBy { it.id }
                val thermal = p.getOrElse(2) { "" }.lines().mapNotNull { line ->
                    val name = line.substringBefore('=').trim()
                    val raw = line.substringAfter('=', "").trim().toFloatOrNull() ?: return@mapNotNull null
                    if (name.isBlank() || name == "-" || raw <= 0f) return@mapNotNull null
                    name to (if (raw > 1000f) raw / 1000f else raw)
                }
                _performance.value = PerformanceInfo(
                    cores = cores,
                    governors = p.getOrElse(1) { "" }.trim().takeIf { it != "-" }?.split(Regex("\\s+"))
                        ?.filter { it.isNotBlank() }.orEmpty(),
                    thermal = thermal,
                    swaps = p.getOrElse(3) { "" }.trim(),
                    zramAlgorithm = p.getOrElse(4) { "" }.trim().takeIf { it != "-" }.orEmpty(),
                    zramSizeBytes = p.getOrElse(5) { "" }.trim().toLongOrNull()?.takeIf { it > 0 }
                )
            } catch (e: Exception) {
                notify("Failed to read performance info: ${e.message}")
            }
        }
    }

    fun setCpuGovernor(governor: String) {
        val available = _performance.value?.governors.orEmpty()
        if (governor !in available) return
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            conn.shellCommand(
                suCommand(
                    "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $governor > \$f; done"
                )
            )
            notify("Governor set to $governor (resets on reboot)")
            loadPerformance()
        }
    }

    fun listRootDirectory(path: String, onResult: (List<FileEntry>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val cmd = "ls -la ${shellQuote(if (path.endsWith("/")) path else "$path/")}"
            val out = conn.shellCommand(if (_rootStatus.value.granted) suCommand(cmd) else cmd)
            val entries = out.lines().mapNotNull { parseLsLine(it, path) }
                .filter { !it.isDirectory }
                .sortedByDescending { it.date }
            withContext(Dispatchers.Main) { onResult(entries) }
        }
    }

    fun readRootFile(path: String, onResult: (String) -> Unit) {
        val cmd = "head -c 300000 ${shellQuote(path)}"
        runForResult(if (_rootStatus.value.granted) suCommand(cmd) else cmd, onResult)
    }

    fun getWakelocks(onResult: (String) -> Unit) = runForResult(
        "dumpsys power | grep -iE 'wake_lock|wakelock|PARTIAL_WAKE' | head -n 300"
    ) { onResult(it.ifBlank { "No active wakelocks reported." }) }

    fun resetBatteryStats() = runCommand("dumpsys batterystats --reset", "Battery stats reset")

    fun getAppPermissions(packageName: String, onResult: (List<AppPermission>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val dump = conn.shellCommand("dumpsys package ${shellQuote(packageName)}")
            val runtime = dump.substringAfter("runtime permissions:", "")
            val perms = Regex("^\\s+([\\w.]+): granted=(true|false)", RegexOption.MULTILINE)
                .findAll(runtime)
                .map { AppPermission(it.groupValues[1], it.groupValues[2] == "true") }
                .distinctBy { it.name }
                .sortedBy { it.name }
                .toList()
            withContext(Dispatchers.Main) { onResult(perms) }
        }
    }

    fun setAppPermission(packageName: String, permission: String, grant: Boolean, onDone: () -> Unit) {
        if (!PERMISSION_REGEX.matches(permission)) return
        viewModelScope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val out = conn.shellCommand(
                "pm ${if (grant) "grant" else "revoke"} ${shellQuote(packageName)} $permission"
            ).trim()
            if (out.isNotBlank()) notify(out.lines().first())
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    // endregion

    // region Pairing (Android 11+ wireless debugging)

    fun startServiceDiscovery() = discovery.start()

    fun stopServiceDiscovery() = discovery.stop()

    /** Pairs using a 6-digit code, then connects. [connectPort] is discovered if not given. */
    fun pairWithCode(host: String, pairingPort: Int, pairingCode: String, connectPort: Int?) {
        clearConnectionError()
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch(Dispatchers.IO) {
            pairThenConnect(host.trim(), pairingPort, pairingCode, connectPort)
        }
    }

    /**
     * Waits for the device that scanned our QR code to advertise the pairing service named
     * [serviceName], then pairs with [password] and connects.
     */
    fun pairWithQr(serviceName: String, password: String) {
        clearConnectionError()
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch(Dispatchers.IO) {
            _pairingState.value = PairingState.Pairing("Waiting for the device to scan the QR code…")
            val service = discovery.services
                .map { list -> list.firstOrNull { it.type == AdbService.Type.PAIRING && it.name == serviceName } }
                .filterNotNull()
                .first()
            pairThenConnect(service.host, service.port, password, null)
        }
    }

    /** Connects to an already-paired device through its wireless-debugging port. */
    fun connectPaired(host: String, connectPort: Int) {
        clearConnectionError()
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch(Dispatchers.IO) { connectPairedDevice(host.trim(), connectPort) }
    }

    fun resetPairingState() {
        pairingJob?.cancel()
        pairingJob = null
        _pairingState.value = PairingState.Idle
    }

    private suspend fun pairThenConnect(host: String, pairingPort: Int, code: String, connectPort: Int?) {
        _pairingState.value = PairingState.Pairing("Pairing with $host:$pairingPort…")
        try {
            wireless.pair(host, pairingPort, code)
        } catch (e: Exception) {
            _pairingState.value = PairingState.Failed(pairingError(e, host, pairingPort))
            return
        }

        val port = connectPort ?: findConnectPort(host)
        if (port == null) {
            _pairingState.value = PairingState.Success(
                "Paired with $host. Enter the port shown under \"IP address & Port\" on the device's " +
                    "Wireless debugging screen, then tap Connect.",
                needsConnectPort = true
            )
            return
        }
        connectPairedDevice(host, port)
    }

    private suspend fun connectPairedDevice(host: String, connectPort: Int) {
        _pairingState.value = PairingState.Pairing("Connecting to $host:$connectPort…")
        try {
            wireless.enableTcpMode(host, connectPort, LEGACY_TCP_PORT)
        } catch (e: Exception) {
            _pairingState.value = PairingState.Failed(
                "Couldn't connect to $host:$connectPort. Use the port shown under \"IP address & Port\" " +
                    "(not the pairing port). ${e.message.orEmpty()}".trim()
            )
            return
        }
        _pairingState.value = PairingState.Pairing("Opening the connection…")
        // adbd restarts in TCP mode; give it a moment to start listening.
        delay(2500)
        _pairingState.value = PairingState.Success("Paired. Connecting to $host:$LEGACY_TCP_PORT…")
        connectWireless(host, LEGACY_TCP_PORT)
    }

    private suspend fun findConnectPort(host: String): Int? = withTimeoutOrNull(5_000) {
        discovery.services
            .map { list ->
                val connect = list.filter { it.type == AdbService.Type.CONNECT }
                connect.firstOrNull { it.host == host }
                    ?: connect.singleOrNull()?.takeIf { host == "127.0.0.1" || host == "localhost" }
            }
            .filterNotNull()
            .first()
            .port
    }

    private fun pairingError(e: Exception, host: String, port: Int): String {
        val message = e.message.orEmpty()
        return when {
            e is java.net.ConnectException ->
                "Can't reach $host:$port. Check the IP and pairing port, and keep the pairing dialog open on the device."
            e is java.net.SocketTimeoutException -> "Timed out pairing with $host:$port."
            message.contains("Broken pipe", true) || message.contains("reset", true) ||
                message.contains("closed", true) || e is javax.net.ssl.SSLException ->
                "The device closed the connection. Check the pairing code, and make sure the pairing dialog " +
                    "is still open (each code works once and expires when the dialog closes)."
            else -> message.ifBlank { "Pairing failed" }
        }
    }

    // endregion

    // region Helpers

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        try {
            getApplication<Application>().contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        return name.replace("/", "_") to size
    }

    private fun progressOf(done: Long, total: Long): Float? =
        if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null

    private fun parseLsLine(line: String, dir: String): FileEntry? {
        val m = LS_REGEX.find(line.trim()) ?: return null
        val perms = m.groupValues[1]
        var name = m.groupValues[6]
        val isLink = perms.startsWith("l")
        if (isLink) name = name.substringBefore(" -> ")
        if (name == "." || name == "..") return null
        return FileEntry(
            name = name,
            path = joinPath(dir, name),
            isDirectory = perms.startsWith("d") || isLink,
            size = m.groupValues[3].trim().toLongOrNull() ?: 0,
            permissions = perms,
            date = "${m.groupValues[4]} ${m.groupValues[5]}",
            isLink = isLink
        )
    }

    override fun onCleared() {
        super.onCleared()
        discovery.stop()
        val conn = connection
        connection = null
        conn?.disconnect()
    }

    companion object {
        private const val SEP = "<<ADBC_SEP>>"
        private const val LEGACY_TCP_PORT = 5555
        private const val ROOT_STAGING_FILE = "/data/local/tmp/adbcontroller_staging"
        private val MODULE_ID_REGEX = Regex("^[A-Za-z0-9._-]+$")
        private val PERMISSION_REGEX = Regex("^[A-Za-z0-9._]+$")
        private val LOG_REGEX =
            Regex("^(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+(.+?):\\s*(.*)$")
        private val LS_REGEX =
            Regex("^([-dlcbps][^\\s]{9,})\\s+(?:\\d+\\s+)?\\S+\\s+(\\S+)\\s+([\\d,\\s]*?)\\s*(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$")

        fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        fun joinPath(dir: String, name: String): String =
            if (dir.endsWith("/")) "$dir$name" else "$dir/$name"

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = listOf("KB", "MB", "GB", "TB")
            var value = bytes / 1024.0
            var unit = 0
            while (value >= 1024 && unit < units.lastIndex) {
                value /= 1024
                unit++
            }
            return "%.1f %s".format(value, units[unit])
        }

        private fun formatUptime(seconds: Long): String {
            val days = seconds / 86_400
            val hours = (seconds % 86_400) / 3_600
            val minutes = (seconds % 3_600) / 60
            return buildString {
                if (days > 0) append("${days}d ")
                if (days > 0 || hours > 0) append("${hours}h ")
                append("${minutes}m")
            }
        }
    }
}
