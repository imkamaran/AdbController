package com.adbcontroller.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.adbcontroller.data.ThemeMode
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.SectionHeader
import com.adbcontroller.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {}
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val recentDevices by viewModel.recentDevices.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeader("Appearance")
            SettingsGroup {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
                )
                ThemeOption(Icons.Default.BrightnessAuto, "System default", themeMode == ThemeMode.SYSTEM) {
                    viewModel.setThemeMode(ThemeMode.SYSTEM)
                }
                ThemeOption(Icons.Default.LightMode, "Light", themeMode == ThemeMode.LIGHT) {
                    viewModel.setThemeMode(ThemeMode.LIGHT)
                }
                ThemeOption(Icons.Default.DarkMode, "Dark", themeMode == ThemeMode.DARK) {
                    viewModel.setThemeMode(ThemeMode.DARK)
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ListItem(
                    headlineContent = { Text("Dynamic color") },
                    supportingContent = {
                        Text(if (dynamicSupported) "Use colors from your wallpaper" else "Requires Android 12 or newer")
                    },
                    leadingContent = { IconBadge(Icons.Default.Palette, AccentColors.Pink, size = 36.dp) },
                    trailingContent = {
                        Switch(
                            checked = dynamicColor && dynamicSupported,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            enabled = dynamicSupported
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }

            SectionHeader("Connections")
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Recent devices") },
                    supportingContent = {
                        Text(if (recentDevices.isEmpty()) "No saved devices" else "${recentDevices.size} saved")
                    },
                    leadingContent = { IconBadge(Icons.Default.History, AccentColors.Blue, size = 36.dp) },
                    trailingContent = {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            enabled = recentDevices.isNotEmpty()
                        ) { Text("Clear") }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }

            SectionHeader("Security")
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("ADB key fingerprint") },
                    supportingContent = {
                        Text(
                            viewModel.keyFingerprint,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = { IconBadge(Icons.Default.Key, AccentColors.Amber, size = 36.dp) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
                Text(
                    "Target devices see this key in their authorization prompt. Revoke it there under " +
                        "Developer options → Revoke USB debugging authorizations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                )
            }

            SectionHeader("About")
            SettingsGroup {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                    headlineContent = { Text("About ADB Controller") },
                    supportingContent = { Text("Version $versionName · Developer · Credits") },
                    leadingContent = { AppLogo(size = 36.dp) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear recent devices?",
            message = "This removes all ${recentDevices.size} saved devices.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = { viewModel.clearRecentDevices() },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}
