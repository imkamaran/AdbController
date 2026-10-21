package com.adbcontroller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.PairingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingCodeScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val pairingState by viewModel.pairingState.collectAsState()
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetPairingState() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with Pairing Code") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "How to find pairing info",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "On the target device:\n" +
                        "1. Go to Settings > Developer Options\n" +
                        "2. Enable Wireless Debugging\n" +
                        "3. Tap 'Pair device with pairing code'\n" +
                        "4. Note the IP address, port, and 6-digit code shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null)
                        }
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Pairing Port") },
                        placeholder = { Text("37xxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Numbers, contentDescription = null)
                        },
                        supportingText = {
                            Text("The port shown in the pairing dialog (not 5555)")
                        }
                    )

                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { if (it.length <= 6) pairingCode = it },
                        label = { Text("Pairing Code") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (canPair(ipAddress, port, pairingCode, pairingState)) {
                                    viewModel.pairDevice(
                                        ipAddress,
                                        port.toIntOrNull() ?: 0,
                                        pairingCode
                                    )
                                }
                            }
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Pin, contentDescription = null)
                        },
                        supportingText = {
                            Text("6-digit code from the target device")
                        }
                    )

                    Button(
                        onClick = {
                            viewModel.pairDevice(
                                ipAddress,
                                port.toIntOrNull() ?: 0,
                                pairingCode
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canPair(ipAddress, port, pairingCode, pairingState)
                    ) {
                        if (pairingState is PairingState.Pairing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pairing...")
                        } else {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pair Device")
                        }
                    }
                }
            }

            when (val state = pairingState) {
                is PairingState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = onBack) {
                                Text("Back to Connect")
                            }
                        }
                    }
                }
                is PairingState.Failed -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

private fun canPair(ip: String, port: String, code: String, state: PairingState): Boolean {
    return ip.isNotBlank() &&
        port.isNotBlank() &&
        port.toIntOrNull() != null &&
        code.length == 6 &&
        state !is PairingState.Pairing
}
