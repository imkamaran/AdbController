package com.adbcontroller.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adbcontroller.R
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.SectionHeader

private const val GITHUB_USER = "imkamaran"
private const val GITHUB_URL = "https://github.com/$GITHUB_USER"
private const val REPO_URL = "$GITHUB_URL/AdbController"

private val features = listOf(
    Triple(Icons.Default.Wifi, "Wireless, USB OTG & pairing", AccentColors.Blue),
    Triple(Icons.Default.TouchApp, "Live remote control", AccentColors.Purple),
    Triple(Icons.Default.Apps, "App, file & APK management", AccentColors.Teal),
    Triple(Icons.Default.Tune, "Quick settings & diagnostics", AccentColors.Indigo),
    Triple(Icons.Default.AdminPanelSettings, "Root tools & module manager", AccentColors.Orange)
)

private val libraries = listOf(
    "Kotlin & Coroutines",
    "Jetpack Compose & Material 3",
    "AndroidX Navigation & Lifecycle",
    "libadb-android (wireless debugging pairing)",
    "Conscrypt",
    "ZXing (QR codes)",
    "Bouncy Castle"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val (versionName, versionCode) = remember {
        runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            (info.versionName ?: "1.0") to info.versionCode.toString()
        }.getOrDefault("1.0" to "1")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppLogo(size = 104.dp)
                Spacer(Modifier.height(16.dp))
                Text("ADB Controller", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Version $versionName ($versionCode)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Control and manage other Android devices from your phone over Wi-Fi or USB — " +
                        "no computer needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            SectionHeader("Developer")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF1B5FD9), Color(0xFF7A4FC4)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                GITHUB_USER.first().uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Made by $GITHUB_USER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "github.com/$GITHUB_USER",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Button(
                        onClick = { uriHandler.openUri(GITHUB_URL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View GitHub profile")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    OutlinedButton(
                        onClick = { uriHandler.openUri(REPO_URL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Source, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Source code")
                    }
                }
            }

            SectionHeader("License")
            Card(
                onClick = { uriHandler.openUri("$REPO_URL/blob/main/LICENSE") },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Gavel, AccentColors.Blue, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GNU GPL v3.0", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Free and open source. Provided without warranty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open license", modifier = Modifier.size(18.dp))
                }
            }

            SectionHeader("Features")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    features.forEach { (icon, label, tint) -> FeatureRow(icon, label, tint) }
                }
            }

            SectionHeader("Privacy")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    IconBadge(Icons.Default.Lock, AccentColors.Green, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "No accounts, analytics or cloud services. The app talks directly to the devices " +
                            "you connect, and your ADB key never leaves this phone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SectionHeader("Built with")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    libraries.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text(
                "© 2026 $GITHUB_USER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
fun AppLogo(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF1B5FD9), Color(0xFF7A4FC4)))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "ADB Controller logo",
            modifier = Modifier.requiredSize(size * 1.5f)
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, label: String, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
