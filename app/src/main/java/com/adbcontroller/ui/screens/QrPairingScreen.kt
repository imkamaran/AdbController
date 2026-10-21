package com.adbcontroller.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adbcontroller.viewmodel.ConnectionState
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.PairingState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPairingScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onConnected: () -> Unit = {}
) {
    val pairingState by viewModel.pairingState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var nonce by remember { mutableIntStateOf(0) }

    // Same format Android Studio uses: the target scans it, then advertises a pairing
    // service with this name and accepts this password.
    val serviceName = remember(nonce) { "adbcontroller-" + randomString(8) }
    val password = remember(nonce) { randomString(10) }
    val qrBitmap = remember(serviceName, password) {
        qrCode("WIFI:T:ADB;S:$serviceName;P:$password;;", 720)
    }

    DisposableEffect(Unit) {
        viewModel.startServiceDiscovery()
        onDispose {
            viewModel.resetPairingState()
            viewModel.stopServiceDiscovery()
        }
    }

    LaunchedEffect(serviceName) {
        viewModel.pairWithQr(serviceName, password)
    }

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) onConnected()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with QR code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { nonce++ }, enabled = pairingState !is PairingState.Success) {
                        Icon(Icons.Default.Refresh, contentDescription = "New QR code")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "On the target device (Android 11+): Settings → Developer options → Wireless debugging → " +
                            "Pair device with QR code, then scan this code. Both devices must be on the same Wi-Fi network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // QR codes need a light background to scan reliably, even in dark mode.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Pairing QR code",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                "This code works once. Tap ↻ for a new one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Box(Modifier.fillMaxWidth()) {
                PairingStatusCard(state = pairingState, connectionState = connectionState)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun randomString(length: Int): String {
    val chars = "abcdefghijkmnpqrstuvwxyz23456789"
    val random = SecureRandom()
    return buildString { repeat(length) { append(chars[random.nextInt(chars.length)]) } }
}

private fun qrCode(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1)
    )
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
