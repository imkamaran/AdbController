package com.adbcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.SectionHeader
import com.adbcontroller.ui.components.TextInputDialog
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.QuickToggle

private data class ToggleSpec(
    val toggle: QuickToggle,
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

private val connectivityToggles = listOf(
    ToggleSpec(QuickToggle.WIFI, "Wi-Fi", Icons.Default.Wifi, AccentColors.Blue),
    ToggleSpec(QuickToggle.BLUETOOTH, "Bluetooth", Icons.Default.Bluetooth, AccentColors.Indigo),
    ToggleSpec(QuickToggle.MOBILE_DATA, "Mobile data", Icons.Default.SignalCellularAlt, AccentColors.Teal),
    ToggleSpec(QuickToggle.AIRPLANE, "Airplane", Icons.Default.AirplanemodeActive, AccentColors.Orange)
)

private val systemToggles = listOf(
    ToggleSpec(QuickToggle.DARK_MODE, "Dark theme", Icons.Default.DarkMode, AccentColors.Purple),
    ToggleSpec(QuickToggle.BATTERY_SAVER, "Battery saver", Icons.Default.BatterySaver, AccentColors.Amber),
    ToggleSpec(QuickToggle.STAY_AWAKE, "Stay awake", Icons.Default.Coffee, AccentColors.Lime)
)

private val developerToggles = listOf(
    ToggleSpec(QuickToggle.SHOW_TOUCHES, "Show taps", Icons.Default.TouchApp, AccentColors.Pink),
    ToggleSpec(QuickToggle.POINTER_LOCATION, "Pointer location", Icons.Default.MyLocation, AccentColors.Cyan),
    ToggleSpec(QuickToggle.LAYOUT_BOUNDS, "Layout bounds", Icons.Default.BorderOuter, AccentColors.Red),
    ToggleSpec(QuickToggle.ANIMATIONS_OFF, "Animations off", Icons.Default.Animation, AccentColors.Slate)
)

private val timeoutOptions = listOf(
    15_000L to "15s", 30_000L to "30s", 60_000L to "1m", 120_000L to "2m",
    300_000L to "5m", 600_000L to "10m", 1_800_000L to "30m"
)

private val fontScaleOptions = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.quickSettings.collectAsState()
    var pendingDangerous by remember { mutableStateOf<Pair<ToggleSpec, Boolean>?>(null) }
    var showDensityDialog by remember { mutableStateOf(false) }
    var showSizeDialog by remember { mutableStateOf(false) }
    var brightness by remember(state.brightness) { mutableFloatStateOf(state.brightness.toFloat()) }

    LaunchedEffect(Unit) { viewModel.loadQuickSettings() }

    fun onToggle(spec: ToggleSpec, enabled: Boolean) {
        val cutsConnection = viewModel.isWireless && !enabled && spec.toggle == QuickToggle.WIFI ||
            viewModel.isWireless && enabled && spec.toggle == QuickToggle.AIRPLANE
        if (cutsConnection) pendingDangerous = spec to enabled
        else viewModel.setToggle(spec.toggle, enabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadQuickSettings() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (!state.loaded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionHeader("Connectivity") }
            item { ToggleGrid(connectivityToggles, state.toggles, ::onToggle) }

            item { SectionHeader("System") }
            item { ToggleGrid(systemToggles, state.toggles, ::onToggle) }

            item { SectionHeader("Developer") }
            item { ToggleGrid(developerToggles, state.toggles, ::onToggle) }

            item { SectionHeader("Display") }
            item {
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.BrightnessMedium, AccentColors.Amber, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Brightness", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("Auto", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = state.autoBrightness, onCheckedChange = { viewModel.setAutoBrightness(it) })
                    }
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = { viewModel.setBrightness(brightness.toInt()) },
                        valueRange = 1f..255f,
                        enabled = !state.autoBrightness
                    )
                }
            }
            item {
                SettingsCard {
                    LabeledRow(Icons.Default.TextFields, AccentColors.Blue, "Font size", "${(state.fontScale * 100).toInt()}%")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fontScaleOptions) { scale ->
                            FilterChip(
                                selected = kotlin.math.abs(state.fontScale - scale) < 0.01f,
                                onClick = { viewModel.setFontScale(scale) },
                                label = { Text("${(scale * 100).toInt()}%") }
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    LabeledRow(
                        Icons.Default.Timer, AccentColors.Teal, "Screen timeout",
                        timeoutOptions.firstOrNull { it.first == state.screenTimeoutMs }?.second
                            ?: "${state.screenTimeoutMs / 1000}s"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(timeoutOptions) { (ms, label) ->
                            FilterChip(
                                selected = state.screenTimeoutMs == ms,
                                onClick = { viewModel.setScreenTimeout(ms) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    LabeledRow(
                        Icons.Default.AspectRatio, AccentColors.Purple, "Resolution",
                        state.overrideSize.ifBlank { state.physicalSize }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showSizeDialog = true }) { Text("Change") }
                        if (state.overrideSize.isNotBlank()) {
                            TextButton(onClick = { viewModel.setResolution(null) }) { Text("Reset to ${state.physicalSize}") }
                        }
                    }
                }
            }
            item {
                SettingsCard {
                    LabeledRow(
                        Icons.Default.GridView, AccentColors.Indigo, "Density (DPI)",
                        state.overrideDensity.ifBlank { state.physicalDensity }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDensityDialog = true }) { Text("Change") }
                        if (state.overrideDensity.isNotBlank()) {
                            TextButton(onClick = { viewModel.setDensity(null) }) { Text("Reset to ${state.physicalDensity}") }
                        }
                    }
                }
            }
        }
    }

    pendingDangerous?.let { (spec, enabled) ->
        ConfirmDialog(
            title = "This may disconnect you",
            message = "You're connected over Wi-Fi. Changing ${spec.label} will likely end this ADB session.",
            confirmLabel = "Continue",
            destructive = true,
            onConfirm = { viewModel.setToggle(spec.toggle, enabled) },
            onDismiss = { pendingDangerous = null }
        )
    }

    if (showDensityDialog) {
        TextInputDialog(
            title = "Set density",
            label = "DPI (e.g. 420)",
            initial = state.overrideDensity.ifBlank { state.physicalDensity },
            confirmLabel = "Apply",
            onConfirm = { value -> value.trim().toIntOrNull()?.let { viewModel.setDensity(it) } },
            onDismiss = { showDensityDialog = false }
        )
    }

    if (showSizeDialog) {
        TextInputDialog(
            title = "Set resolution",
            label = "Width x height (e.g. 1080x2400)",
            initial = state.overrideSize.ifBlank { state.physicalSize },
            confirmLabel = "Apply",
            onConfirm = { value ->
                val clean = value.trim().lowercase().replace(" ", "")
                if (Regex("\\d+x\\d+").matches(clean)) viewModel.setResolution(clean)
            },
            onDismiss = { showSizeDialog = false }
        )
    }
}

@Composable
private fun ToggleGrid(
    specs: List<ToggleSpec>,
    states: Map<QuickToggle, Boolean>,
    onToggle: (ToggleSpec, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        specs.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { spec ->
                    val on = states[spec.toggle] == true
                    ToggleTile(spec, on, Modifier.weight(1f)) { onToggle(spec, !on) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToggleTile(spec: ToggleSpec, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val container = if (on) spec.tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            spec.icon,
            contentDescription = null,
            tint = if (on) spec.tint else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (on) "On" else "Off",
                style = MaterialTheme.typography.labelSmall,
                color = if (on) spec.tint else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun LabeledRow(icon: ImageVector, tint: Color, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
