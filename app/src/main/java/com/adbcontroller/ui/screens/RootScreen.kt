package com.adbcontroller.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.SectionHeader
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.RootModule
import com.adbcontroller.viewmodel.RootStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val status by viewModel.rootStatus.collectAsState()
    val modules by viewModel.modules.collectAsState()
    val installLog by viewModel.moduleInstallLog.collectAsState()
    var confirmSoftReboot by remember { mutableStateOf(false) }
    var confirmReboot by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<RootModule?>(null) }
    val rootOutput by viewModel.rootOutput.collectAsState()
    var confirmSystemUi by remember { mutableStateOf(false) }
    var confirmBulk by remember { mutableStateOf<Boolean?>(null) }
    var moduleMenu by remember { mutableStateOf(false) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.installModule(it) }
    }

    LaunchedEffect(Unit) {
        if (!status.checked) viewModel.checkRoot() else if (status.granted) viewModel.loadModules()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Root") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkRoot() }, enabled = !status.checking) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check again")
                    }
                }
            )
        },
        floatingActionButton = {
            if (status.granted && status.manager != null) {
                ExtendedFloatingActionButton(
                    onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    text = { Text("Install module") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { RootStatusCard(status, onRetry = { viewModel.checkRoot() }) }

            if (status.granted) {
                item { SectionHeader("Root tools") }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolChip(Icons.Default.Terminal, "Root shell") {
                            viewModel.setRootShell(true)
                            onNavigate("shell")
                        }
                        ToolChip(Icons.Default.FolderSpecial, "Root files") {
                            viewModel.setRootFileMode(true)
                            onNavigate("files")
                        }
                        ToolChip(Icons.Default.Speed, "Performance") { onNavigate("performance") }
                        ToolChip(Icons.Default.MonitorHeart, "Diagnostics") { onNavigate("diagnostics") }
                        ToolChip(Icons.Default.DeveloperBoard, "Kernel log") { onNavigate("kernel_log") }
                        if (status.manager == com.adbcontroller.viewmodel.RootManager.MAGISK) {
                            ToolChip(Icons.Default.Description, "Magisk log") { viewModel.showMagiskLog() }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolChip(Icons.Default.Refresh, "Restart SystemUI") { confirmSystemUi = true }
                        ToolChip(Icons.Default.Autorenew, "Soft reboot") { confirmSoftReboot = true }
                        ToolChip(Icons.Default.RestartAlt, "Reboot") { confirmReboot = true }
                    }
                }

                item {
                    SectionHeader(
                        "Modules" + (modules?.let { " (${it.size})" } ?: "")
                    ) {
                        Box {
                            IconButton(onClick = { moduleMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Module options")
                            }
                            DropdownMenu(expanded = moduleMenu, onDismissRequest = { moduleMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Reload") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    onClick = { moduleMenu = false; viewModel.loadModules() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Enable all") },
                                    leadingIcon = { Icon(Icons.Default.ToggleOn, null) },
                                    onClick = { moduleMenu = false; confirmBulk = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Disable all (safe mode)") },
                                    leadingIcon = { Icon(Icons.Default.ToggleOff, null) },
                                    onClick = { moduleMenu = false; confirmBulk = false }
                                )
                            }
                        }
                    }
                }

                val list = modules
                when {
                    list == null -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    list.isEmpty() -> item {
                        Text(
                            "No modules installed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    else -> items(list, key = { it.id }) { module ->
                        ModuleCard(
                            module = module,
                            onToggle = { viewModel.setModuleEnabled(module, it) },
                            onRemove = { removeTarget = module },
                            onRestore = { viewModel.setModuleRemoval(module, false) },
                            onAction = { viewModel.runModuleAction(module) }
                        )
                    }
                }

                if (modules?.any { it.pendingRemove || it.pendingUpdate } == true) {
                    item {
                        Text(
                            "Module changes take effect after a reboot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    removeTarget?.let { module ->
        ConfirmDialog(
            title = "Remove ${module.name}?",
            message = "The module will be uninstalled on the next reboot. You can restore it until then.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { viewModel.setModuleRemoval(module, true) },
            onDismiss = { removeTarget = null }
        )
    }

    confirmBulk?.let { enable ->
        ConfirmDialog(
            title = if (enable) "Enable all modules?" else "Disable all modules?",
            message = if (enable) "Every installed module will be enabled after the next reboot."
            else "Every module will be disabled after the next reboot. Use this to recover from a module " +
                "that causes problems.",
            confirmLabel = if (enable) "Enable all" else "Disable all",
            onConfirm = { viewModel.setAllModulesEnabled(enable) },
            onDismiss = { confirmBulk = null }
        )
    }

    if (confirmSystemUi) {
        ConfirmDialog(
            title = "Restart SystemUI?",
            message = "The status bar, navigation bar and lock screen will restart. Apps keep running.",
            confirmLabel = "Restart",
            onConfirm = { viewModel.restartSystemUi() },
            onDismiss = { confirmSystemUi = false }
        )
    }

    rootOutput?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRootOutput() },
            title = { Text(title) },
            text = {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.dismissRootOutput() }) { Text("Close") } }
        )
    }

    if (confirmSoftReboot) {
        ConfirmDialog(
            title = "Soft reboot?",
            message = "Restarts the Android framework without rebooting the kernel. Open apps will close.",
            confirmLabel = "Soft reboot",
            onConfirm = { viewModel.softReboot() },
            onDismiss = { confirmSoftReboot = false }
        )
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = "Reboot device?",
            message = "Reboot now to apply module changes.",
            confirmLabel = "Reboot",
            onConfirm = { viewModel.reboot() },
            onDismiss = { confirmReboot = false }
        )
    }

    installLog?.let { log ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissModuleLog() },
            title = { Text("Module installer") },
            text = {
                Text(
                    log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissModuleLog()
                    confirmReboot = true
                }) { Text("Reboot now") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissModuleLog() }) { Text("Later") }
            }
        )
    }
}

@Composable
private fun RootStatusCard(status: RootStatus, onRetry: () -> Unit) {
    val (icon, tint, title) = when {
        !status.checked || status.checking -> Triple(Icons.Default.HourglassTop, AccentColors.Slate, "Checking root access…")
        status.granted -> Triple(Icons.Default.VerifiedUser, AccentColors.Green, "Root access granted")
        status.hasSu -> Triple(Icons.Default.GppMaybe, AccentColors.Amber, "Root not granted")
        else -> Triple(Icons.Default.GppBad, AccentColors.Slate, "Device is not rooted")
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, tint, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val details = listOfNotNull(
                        status.manager?.let { m ->
                            m.label + status.managerVersion.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                        },
                        status.selinux.takeIf { it.isNotBlank() }?.let { "SELinux $it" }
                    )
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (status.checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            }
            if (status.checked && !status.checking && status.hasSu && !status.granted) {
                Text(
                    "The target has su, but this session was refused. Open Magisk, KernelSU or APatch on " +
                        "the target, allow superuser access for Shell, then check again.",
                    style = MaterialTheme.typography.bodySmall
                )
                FilledTonalButton(onClick = onRetry) { Text("Check again") }
            }
            if (status.checked && !status.checking && !status.hasSu) {
                Text(
                    "No su binary was found on the target. Root features are only available on devices you have rooted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun ModuleCard(
    module: RootModule,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit,
    onAction: () -> Unit
) {
    val tint = when {
        module.pendingRemove -> AccentColors.Red
        module.enabled -> AccentColors.Purple
        else -> AccentColors.Slate
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.Extension, tint, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        module.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (module.pendingRemove) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(module.version, module.author.takeIf { it.isNotBlank() }?.let { "by $it" })
                            .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onToggle,
                    enabled = !module.pendingRemove
                )
            }
            if (module.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    module.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (module.pendingUpdate) StatusBadge("Update pending", AccentColors.Blue)
                if (module.pendingRemove) StatusBadge("Removed on reboot", AccentColors.Red)
                if (!module.enabled && !module.pendingRemove) StatusBadge("Disabled", AccentColors.Slate)
                Spacer(Modifier.weight(1f))
                if (module.hasAction && module.enabled && !module.pendingRemove) {
                    TextButton(onClick = onAction) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Action")
                    }
                }
                if (module.pendingRemove) {
                    TextButton(onClick = onRestore) { Text("Restore") }
                } else {
                    TextButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(end = 6.dp)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
