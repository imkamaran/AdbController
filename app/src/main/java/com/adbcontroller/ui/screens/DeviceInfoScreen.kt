package com.adbcontroller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.adbcontroller.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshDeviceInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Information") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshDeviceInfo() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    InfoSection("Device", Icons.Default.PhoneAndroid) {
                        InfoRow("Model", deviceInfo.model)
                        InfoRow("Manufacturer", deviceInfo.manufacturer)
                        InfoRow("Serial Number", deviceInfo.serialNumber)
                        InfoRow("Build ID", deviceInfo.buildId)
                    }
                }
                item {
                    InfoSection("Software", Icons.Default.Android) {
                        InfoRow("Android Version", deviceInfo.androidVersion)
                        InfoRow("SDK Level", deviceInfo.sdkVersion)
                        InfoRow("Kernel", deviceInfo.kernelVersion)
                        InfoRow("CPU Architecture", deviceInfo.cpuArch)
                        InfoRow("Uptime", deviceInfo.uptime)
                    }
                }
                item {
                    InfoSection("Display", Icons.Default.Screenshot) {
                        InfoRow("Resolution", deviceInfo.screenResolution)
                    }
                }
                item {
                    InfoSection("Battery", Icons.Default.BatteryFull) {
                        InfoRow("Level", "${deviceInfo.batteryLevel}%")
                        InfoRow("Status", deviceInfo.batteryStatus)
                        InfoRow("Temperature", deviceInfo.batteryTemp)
                    }
                }
                item {
                    InfoSection("Memory", Icons.Default.Memory) {
                        InfoRow("Total RAM", deviceInfo.totalRam)
                        InfoRow("Available RAM", deviceInfo.availableRam)
                    }
                }
                item {
                    InfoSection("Storage", Icons.Default.Storage) {
                        InfoRow("Total", deviceInfo.totalStorage)
                        InfoRow("Available", deviceInfo.availableStorage)
                    }
                }
                item {
                    InfoSection("Network", Icons.Default.Wifi) {
                        InfoRow("IP Address", deviceInfo.ip)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
