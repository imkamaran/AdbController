package com.adbcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.SectionHeader
import com.adbcontroller.viewmodel.CpuCore
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.FileEntry
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val info by viewModel.performance.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    var pendingGovernor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.loadPerformance()
            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Performance")
                        Text(
                            "Refreshes every 3 seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val data = info
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionHeader("CPU cores (${data.cores.size})") }
            items(data.cores, key = { it.id }) { core -> CoreRow(core) }

            if (data.governors.isNotEmpty()) {
                item { SectionHeader("Governor") }
                item {
                    val current = data.cores.firstOrNull { it.online }?.governor
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.governors) { gov ->
                            FilterChip(
                                selected = gov == current,
                                onClick = { if (gov != current) pendingGovernor = gov },
                                enabled = rootStatus.granted,
                                label = { Text(gov) }
                            )
                        }
                    }
                }
                item {
                    Text(
                        if (rootStatus.granted) "Applies to all cores and resets on reboot."
                        else "Changing the governor requires root.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (data.thermal.isNotEmpty()) {
                item { SectionHeader("Temperatures") }
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            data.thermal.forEach { (name, temp) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(
                                        "%.1f °C".format(temp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            temp >= 70f -> AccentColors.Red
                                            temp >= 50f -> AccentColors.Amber
                                            else -> AccentColors.Green
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Memory compression") }
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val algo = Regex("\\[(\\w+)]").find(data.zramAlgorithm)?.groupValues?.get(1)
                            ?: data.zramAlgorithm
                        InfoLine("ZRAM size", data.zramSizeBytes?.let { DeviceViewModel.formatBytes(it) } ?: "Not configured")
                        if (algo.isNotBlank()) InfoLine("Algorithm", algo)
                        if (data.swaps.lines().size > 1) {
                            Text(
                                data.swaps,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    pendingGovernor?.let { gov ->
        ConfirmDialog(
            title = "Switch to $gov?",
            message = "All CPU cores will use the $gov governor until the next reboot. " +
                "Some governors can affect battery life or stability.",
            confirmLabel = "Apply",
            onConfirm = { viewModel.setCpuGovernor(gov) },
            onDismiss = { pendingGovernor = null }
        )
    }
}

@Composable
private fun CoreRow(core: CpuCore) {
    val fraction = if (core.curKhz != null && core.maxKhz != null && core.maxKhz > 0)
        (core.curKhz.toFloat() / core.maxKhz).coerceIn(0f, 1f) else null
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Memory, if (core.online) AccentColors.Indigo else AccentColors.Slate, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("CPU ${core.id}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (!core.online) "Offline"
                        else listOf(core.governor, rangeText(core)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    core.curKhz?.takeIf { core.online }?.let { "${it / 1000} MHz" } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (fraction != null && core.online) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    drawStopIndicator = {}
                )
            }
        }
    }
}

private fun rangeText(core: CpuCore): String {
    val min = core.minKhz ?: return ""
    val max = core.maxKhz ?: return ""
    return "${min / 1000}–${max / 1000} MHz"
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val tabs = listOf("Crashes", "ANRs", "Wakelocks", "Battery")
    var tab by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<FileEntry>?>(null) }
    var wakelocks by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, reload) {
        when (tab) {
            0 -> { files = null; viewModel.listRootDirectory("/data/tombstones") { files = it } }
            1 -> { files = null; viewModel.listRootDirectory("/data/anr") { files = it } }
            2 -> { wakelocks = null; viewModel.getWakelocks { wakelocks = it } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            when (tab) {
                0, 1 -> {
                    val list = files
                    when {
                        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (tab == 0) "No crash reports (tombstones) found.\nReading them usually requires root."
                                else "No ANR traces found.\nReading them usually requires root.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> LazyColumn(Modifier.fillMaxSize()) {
                            items(list, key = { it.path }) { file ->
                                ListItem(
                                    modifier = Modifier.clickable {
                                        viewing = file.name to null
                                        viewModel.readRootFile(file.path) { viewing = file.name to it }
                                    },
                                    headlineContent = { Text(file.name) },
                                    supportingContent = {
                                        Text("${file.date} · ${DeviceViewModel.formatBytes(file.size)}")
                                    },
                                    leadingContent = {
                                        IconBadge(
                                            if (tab == 0) Icons.Default.BugReport else Icons.Default.HourglassBottom,
                                            if (tab == 0) AccentColors.Red else AccentColors.Amber
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    val text = wakelocks
                    if (text == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        MonoText(text)
                    }
                }
                3 -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Default.BatteryChargingFull, AccentColors.Lime)
                                Spacer(Modifier.width(12.dp))
                                Text("Battery usage stats", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Clears the battery usage history shown in Settings → Battery. Useful after " +
                                    "installing new firmware or modules to measure drain from a clean slate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset battery stats") }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(name) },
            text = {
                if (content == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } }
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset battery stats?",
            message = "Battery usage history on the device will be cleared.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = { viewModel.resetBatteryStats() },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun MonoText(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A))
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFFB0BEC5),
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
