package com.adbcontroller.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.FunctionButton
import com.adbcontroller.ui.components.SectionHeader
import com.adbcontroller.ui.components.ThemeToggleButton
import com.adbcontroller.viewmodel.DeviceInfo
import com.adbcontroller.viewmodel.DeviceViewModel

private data class DashboardAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DeviceViewModel,
    onNavigate: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val address by viewModel.connectedAddress.collectAsState()
    var showRebootDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.installApk(it) }
    }

    val control = listOf(
        DashboardAction(Icons.Default.TouchApp, "Remote Control", AccentColors.Purple) { onNavigate("remote") },
        DashboardAction(Icons.Default.Terminal, "Shell", AccentColors.Green) { onNavigate("shell") },
        DashboardAction(Icons.Default.Tune, "Quick Settings", AccentColors.Indigo) { onNavigate("quick_settings") },
        DashboardAction(Icons.Default.Screenshot, "Screenshot", AccentColors.Pink) { onNavigate("screenshot") }
    )
    val manage = listOf(
        DashboardAction(Icons.Default.Apps, "Apps", AccentColors.Blue) { onNavigate("packages") },
        DashboardAction(Icons.Default.InstallMobile, "Install APK", AccentColors.Teal) {
            apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        },
        DashboardAction(Icons.Default.Folder, "Files", AccentColors.Orange) { onNavigate("files") },
        DashboardAction(Icons.Default.Memory, "Processes", AccentColors.Cyan) { onNavigate("processes") }
    )
    val diagnostics = listOf(
        DashboardAction(Icons.Default.Info, "Device Info", AccentColors.Blue) { onNavigate("device_info") },
        DashboardAction(Icons.Default.BugReport, "Logcat", AccentColors.Red) { onNavigate("logcat") },
        DashboardAction(Icons.Default.BatteryChargingFull, "Battery", AccentColors.Lime) { onNavigate("battery") },
        DashboardAction(Icons.Default.NetworkCheck, "Network", AccentColors.Indigo) { onNavigate("network") },
        DashboardAction(Icons.AutoMirrored.Filled.ListAlt, "Properties", AccentColors.Slate) { onNavigate("properties") }
    )
    val root = listOf(
        DashboardAction(Icons.Default.AdminPanelSettings, "Root & Modules", AccentColors.Purple) { onNavigate("root") },
        DashboardAction(Icons.Default.Speed, "Performance", AccentColors.Orange) { onNavigate("performance") },
        DashboardAction(Icons.Default.MonitorHeart, "Diagnostics", AccentColors.Red) { onNavigate("diagnostics") },
        DashboardAction(Icons.Default.DeveloperBoard, "Kernel Log", AccentColors.Slate) { onNavigate("kernel_log") }
    )
    val power = listOf(
        DashboardAction(Icons.Default.RestartAlt, "Reboot", AccentColors.Red) { showRebootDialog = true },
        DashboardAction(Icons.Default.Wifi, "WiFi ADB", AccentColors.Cyan) { onNavigate("wifi_adb") }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard", fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AccentColors.Green)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Connected · $address",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    ThemeToggleButton(viewModel)
                    IconButton(onClick = { showDisconnectConfirm = true }) {
                        Icon(
                            Icons.Default.LinkOff,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Refresh device info") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { menuOpen = false; viewModel.refreshDeviceInfo() }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menuOpen = false; onNavigate("settings") }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = { menuOpen = false; onNavigate("about") }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                DeviceHeroCard(deviceInfo, onClick = { onNavigate("device_info") })
            }
            section("Control", control)
            section("Manage", manage)
            section("Diagnostics", diagnostics)
            section("Root", root)
            section("Power & connection", power)
        }
    }

    if (showRebootDialog) {
        RebootDialog(
            onSelect = { mode ->
                showRebootDialog = false
                viewModel.reboot(mode)
            },
            onDismiss = { showRebootDialog = false }
        )
    }

    if (showDisconnectConfirm) {
        ConfirmDialog(
            title = "Disconnect?",
            message = "End the ADB session with ${deviceInfo.model.ifBlank { address }}.",
            confirmLabel = "Disconnect",
            destructive = true,
            onConfirm = onDisconnect,
            onDismiss = { showDisconnectConfirm = false }
        )
    }
}

private fun LazyGridScope.section(title: String, actions: List<DashboardAction>) {
    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(title) }
    actions.forEach { action ->
        item {
            FunctionButton(
                icon = action.icon,
                label = action.label,
                iconTint = action.tint,
                onClick = action.onClick
            )
        }
    }
}

@Composable
private fun DeviceHeroCard(info: DeviceInfo, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer))
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (info.model.isBlank()) {
                        Text(
                            "Reading device info…",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onPrimaryContainer
                        )
                    } else {
                        val maker = info.manufacturer.replaceFirstChar { it.uppercase() }
                        Text(
                            if (info.model.startsWith(maker, ignoreCase = true)) info.model else "$maker ${info.model}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOf(
                                "Android ${info.androidVersion}",
                                "API ${info.sdkVersion}",
                                info.cpuArch
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Device details",
                    tint = scheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            if (info.model.isBlank()) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val battery = info.batteryLevel.toIntOrNull()
                    StatMeter(
                        icon = if (info.batteryStatus.equals("Charging", true)) Icons.Default.BatteryChargingFull
                        else Icons.Default.BatteryFull,
                        label = "Battery",
                        value = battery?.let { "$it%" } ?: "—",
                        fraction = battery?.div(100f),
                        color = when {
                            battery == null -> AccentColors.Slate
                            battery <= 15 -> AccentColors.Red
                            battery <= 35 -> AccentColors.Amber
                            else -> AccentColors.Green
                        },
                        modifier = Modifier.weight(1f)
                    )
                    StatMeter(
                        icon = Icons.Default.Memory,
                        label = "RAM",
                        value = if (info.ramUsedPercent >= 0) "${info.ramUsedPercent}%" else "—",
                        fraction = info.ramUsedPercent.takeIf { it >= 0 }?.div(100f),
                        color = AccentColors.Indigo,
                        modifier = Modifier.weight(1f)
                    )
                    StatMeter(
                        icon = Icons.Default.Storage,
                        label = "Storage",
                        value = if (info.storageUsedPercent >= 0) "${info.storageUsedPercent}%" else "—",
                        fraction = info.storageUsedPercent.takeIf { it >= 0 }?.div(100f),
                        color = AccentColors.Orange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMeter(
    icon: ImageVector,
    label: String,
    value: String,
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.18f),
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun RebootDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Triple("", "Reboot", Icons.Default.RestartAlt),
        Triple("recovery", "Recovery mode", Icons.Default.Healing),
        Triple("bootloader", "Bootloader / fastboot", Icons.Default.DeveloperBoard),
        Triple("poweroff", "Power off", Icons.Default.PowerSettingsNew)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
        title = { Text("Reboot device") },
        text = {
            Column {
                options.forEach { (mode, label, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (mode == "poweroff") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
