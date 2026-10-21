package com.adbcontroller.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adbcontroller.data.ConnectionType
import com.adbcontroller.data.RecentDevice
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.SectionHeader
import com.adbcontroller.ui.components.TextInputDialog
import com.adbcontroller.ui.components.ThemeToggleButton
import com.adbcontroller.viewmodel.ConnectionState
import com.adbcontroller.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: DeviceViewModel,
    onConnected: () -> Unit,
    onNavigateToPairingCode: () -> Unit = {},
    onNavigateToQrPairing: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val recentDevices by viewModel.recentDevices.collectAsState()
    var ipAddress by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("5555") }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RecentDevice?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val connecting = connectionState as? ConnectionState.Connecting

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) onConnected()
    }

    fun connect() {
        if (ipAddress.isNotBlank()) viewModel.connectWireless(ipAddress, port.toIntOrNull() ?: 5555)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ADB Controller", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Control Android devices over ADB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Box(Modifier.padding(start = 12.dp, end = 4.dp)) { AppLogo(size = 36.dp) }
                },
                actions = {
                    ThemeToggleButton(viewModel)
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (connectionState is ConnectionState.Error) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                (connectionState as ConnectionState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearConnectionError() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            if (recentDevices.isNotEmpty()) {
                item {
                    SectionHeader("Recent devices") {
                        TextButton(onClick = { showClearConfirm = true }) { Text("Clear all") }
                    }
                }
                items(recentDevices, key = { it.id }) { device ->
                    RecentDeviceCard(
                        device = device,
                        isConnecting = connecting?.target == device.address && device.type == ConnectionType.WIFI,
                        enabled = connecting == null,
                        onConnect = { viewModel.connectRecent(device) },
                        onRename = { renameTarget = device },
                        onRemove = { viewModel.removeRecentDevice(device.id) },
                        onEdit = {
                            ipAddress = device.host
                            port = device.port.toString()
                        }
                    )
                }
            }

            item { SectionHeader("New connection") }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardTitle(Icons.Default.Wifi, AccentColors.Blue, "Wireless ADB", "Connect by IP address")

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it.trim() },
                                label = { Text("IP address") },
                                placeholder = { Text("192.168.1.100") },
                                singleLine = true,
                                modifier = Modifier.weight(0.64f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next
                                )
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = { v -> port = v.filter { it.isDigit() }.take(5) },
                                label = { Text("Port") },
                                singleLine = true,
                                modifier = Modifier.weight(0.36f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(onGo = { connect() })
                            )
                        }

                        Button(
                            onClick = { connect() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = ipAddress.isNotBlank() && connecting == null
                        ) {
                            if (connecting != null && connecting.target == "$ipAddress:${port.ifBlank { "5555" }}") {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Connecting…")
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Connect")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardTitle(
                            Icons.Default.PhonelinkSetup, AccentColors.Purple,
                            "Pair device", "Android 11+ wireless debugging"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = onNavigateToQrPairing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("QR code")
                            }
                            FilledTonalButton(
                                onClick = onNavigateToPairingCode,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pairing code")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardTitle(
                            Icons.Default.Usb, AccentColors.Teal,
                            "USB OTG", "Plug in a device — it's detected automatically",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGuide = !showGuide }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Setup guide",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showGuide) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        AnimatedVisibility(visible = showGuide) {
                            Text(
                                "1. On the target device, enable Developer options (tap Build number 7 times).\n" +
                                    "2. Turn on USB debugging.\n" +
                                    "3. Wireless (Android 11+): turn on Wireless debugging and pair with a QR code or pairing code.\n" +
                                    "4. Wireless (older): connect once by USB and use WiFi ADB from the dashboard.\n" +
                                    "5. USB: connect with an OTG cable and accept the authorization prompt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    renameTarget?.let { device ->
        TextInputDialog(
            title = "Rename device",
            label = "Name",
            initial = device.displayName,
            confirmLabel = "Save",
            onConfirm = { viewModel.renameRecentDevice(device.id, it) },
            onDismiss = { renameTarget = null }
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear recent devices?",
            message = "This removes all ${recentDevices.size} saved devices from the list.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = { viewModel.clearRecentDevices() },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun CardTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = icon, tint = tint, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentDeviceCard(
    device: RecentDevice,
    isConnecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isWifi = device.type == ConnectionType.WIFI

    Card(
        onClick = onConnect,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = if (isWifi) Icons.Default.PhoneAndroid else Icons.Default.Usb,
                tint = if (isWifi) AccentColors.Blue else AccentColors.Teal,
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = buildList {
                    add(if (isWifi) device.address else "USB")
                    if (device.androidVersion.isNotBlank()) add("Android ${device.androidVersion}")
                }.joinToString(" · ")
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Last connected " + DateUtils.getRelativeTimeSpanString(
                        device.lastConnected,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (isWifi) {
                            DropdownMenuItem(
                                text = { Text("Connect") },
                                leadingIcon = { Icon(Icons.Default.Link, null) },
                                onClick = { menuOpen = false; onConnect() }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit address") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuOpen = false; onEdit() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                            onClick = { menuOpen = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                }
            }
        }
    }
}
