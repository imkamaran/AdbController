package com.adbcontroller.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.adbcontroller.adb.PairingController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adbcontroller.adb.AdbService
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.SectionHeader
import com.adbcontroller.viewmodel.ConnectionState
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.PairingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingCodeScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit = {}
) {
    val pairingState by viewModel.pairingState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val services by viewModel.nearbyServices.collectAsState()

    var ipAddress by remember { mutableStateOf("") }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("") }

    val pairingServices = services.filter { it.type == AdbService.Type.PAIRING }
    val busy = pairingState is PairingState.Pairing || connectionState is ConnectionState.Connecting
    val context = LocalContext.current
    val localAddresses = remember(services) { PairingController.localAddresses() }

    fun beginSelfPairing() {
        viewModel.startSelfPairing()
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Pair even if notification permission is denied; the code can still be typed in the app
    // when pairing another device, and Android may still show the notification on older versions.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { beginSelfPairing() }

    DisposableEffect(Unit) {
        viewModel.startServiceDiscovery()
        onDispose {
            viewModel.resetPairingState()
            viewModel.stopServiceDiscovery()
        }
    }

    // Fill in the connect port automatically once the device's connect service is seen.
    LaunchedEffect(services, ipAddress) {
        if (connectPort.isBlank() && ipAddress.isNotBlank()) {
            services.firstOrNull { it.type == AdbService.Type.CONNECT && it.host == ipAddress }
                ?.let { connectPort = it.port.toString() }
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) onConnected()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with pairing code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "On the target device (Android 11+): Settings → Developer options → Wireless debugging → " +
                            "Pair device with pairing code. Keep that dialog open and enter the code below. " +
                            "Both devices must be on the same Wi-Fi network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            SectionHeader("This phone")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.PhoneAndroid, AccentColors.Teal, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Pair this phone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Like Shizuku: enter the code in a notification so the pairing dialog stays open.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                beginSelfPairing()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pair via notification")
                    }
                }
            }

            SectionHeader("Found on your network")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                if (pairingServices.isEmpty()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Looking for devices showing a pairing code… You can also enter the details manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column {
                        pairingServices.forEach { service ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    ipAddress = service.host
                                    pairingPort = service.port.toString()
                                    connectPort = ""
                                },
                                headlineContent = {
                                    Text(
                                        if (service.host in localAddresses) "This phone (${service.host}:${service.port})"
                                        else "${service.host}:${service.port}"
                                    )
                                },
                                supportingContent = { Text(service.name, style = MaterialTheme.typography.bodySmall) },
                                leadingContent = { IconBadge(Icons.Default.PhonelinkSetup, AccentColors.Purple) },
                                trailingContent = {
                                    if (service.host == ipAddress && service.port.toString() == pairingPort) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = AccentColors.Green)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            )
                        }
                    }
                }
            }

            SectionHeader("Pairing details")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it.trim() },
                        label = { Text("IP address") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pairingPort,
                            onValueChange = { v -> pairingPort = v.filter { it.isDigit() }.take(5) },
                            label = { Text("Pairing port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { v -> pairingCode = v.filter { it.isDigit() }.take(6) },
                            label = { Text("Pairing code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next)
                        )
                    }
                    OutlinedTextField(
                        value = connectPort,
                        onValueChange = { v -> connectPort = v.filter { it.isDigit() }.take(5) },
                        label = { Text("Connect port (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        supportingText = {
                            Text("The port under \"IP address & Port\" on the Wireless debugging screen. Detected automatically when possible.")
                        }
                    )
                    Button(
                        onClick = {
                            viewModel.pairWithCode(
                                ipAddress,
                                pairingPort.toInt(),
                                pairingCode,
                                connectPort.toIntOrNull()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !busy && ipAddress.isNotBlank() && pairingPort.toIntOrNull() != null &&
                            pairingCode.length == 6
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pair and connect")
                    }
                }
            }

            PairingStatusCard(
                state = pairingState,
                connectionState = connectionState,
                onConnect = {
                    connectPort.toIntOrNull()?.let { viewModel.connectPaired(ipAddress, it) }
                },
                canConnect = connectPort.toIntOrNull() != null && ipAddress.isNotBlank()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PairingStatusCard(
    state: PairingState,
    connectionState: ConnectionState,
    onConnect: () -> Unit = {},
    canConnect: Boolean = false
) {
    val connectError = (connectionState as? ConnectionState.Error)?.message
    when {
        state is PairingState.Pairing || (state is PairingState.Success && connectionState is ConnectionState.Connecting) -> {
            val text = (state as? PairingState.Pairing)?.step ?: "Connecting… approve the prompt on the device if one appears."
            StatusCard(MaterialTheme.colorScheme.surfaceContainerHigh) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        state is PairingState.Success && connectError != null -> {
            StatusCard(MaterialTheme.colorScheme.errorContainer) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Paired, but connecting failed: $connectError",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        state is PairingState.Success -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentColors.Green)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (state.needsConnectPort) {
                        Button(onClick = onConnect, enabled = canConnect, modifier = Modifier.fillMaxWidth()) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
        state is PairingState.Failed -> {
            StatusCard(MaterialTheme.colorScheme.errorContainer) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun StatusCard(color: androidx.compose.ui.graphics.Color, content: @Composable RowScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
