package com.adbcontroller.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.viewmodel.AppDetails
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.PackageInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageManagerScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val packages by viewModel.packages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showSystemApps by rememberSaveableBoolean(false)
    var onlyDisabled by rememberSaveableBoolean(false)
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackage by remember { mutableStateOf<PackageInfo?>(null) }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.installApk(it, showSystemApps) }
    }

    LaunchedEffect(showSystemApps) { viewModel.loadPackages(showSystemApps) }

    val filtered = packages.filter {
        (searchQuery.isBlank() || it.packageName.contains(searchQuery, ignoreCase = true)) &&
            (!onlyDisabled || it.isDisabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPackages(showSystemApps) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                },
                icon = { Icon(Icons.Default.InstallMobile, contentDescription = null) },
                text = { Text("Install APK") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search packages") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = { Text("System apps") },
                    leadingIcon = if (showSystemApps) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = onlyDisabled,
                    onClick = { onlyDisabled = !onlyDisabled },
                    label = { Text("Disabled") },
                    leadingIcon = if (onlyDisabled) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${filtered.size} apps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoading && packages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.packageName }) { pkg ->
                        ListItem(
                            modifier = Modifier.clickable { selectedPackage = pkg },
                            headlineContent = {
                                Text(
                                    pkg.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    pkg.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                IconBadge(
                                    icon = if (pkg.isSystemApp) Icons.Default.Android else Icons.Default.Apps,
                                    tint = when {
                                        pkg.isDisabled -> AccentColors.Slate
                                        pkg.isSystemApp -> AccentColors.Green
                                        else -> AccentColors.Blue
                                    }
                                )
                            },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (pkg.isDisabled) Tag("Disabled", AccentColors.Slate)
                                    if (pkg.isSystemApp) Tag("System", AccentColors.Green)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    selectedPackage?.let { pkg ->
        AppActionsSheet(
            pkg = pkg,
            viewModel = viewModel,
            showSystemApps = showSystemApps,
            onDismiss = { selectedPackage = null }
        )
    }
}

@Composable
private fun rememberSaveableBoolean(initial: Boolean) =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }

@Composable
private fun Tag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppActionsSheet(
    pkg: PackageInfo,
    viewModel: DeviceViewModel,
    showSystemApps: Boolean,
    onDismiss: () -> Unit
) {
    var details by remember(pkg.packageName) { mutableStateOf<AppDetails?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var showPermissions by remember { mutableStateOf(false) }

    val apkSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri -> uri?.let { viewModel.extractApk(pkg.packageName, it) } }

    LaunchedEffect(pkg.packageName) {
        viewModel.getAppDetails(pkg.packageName) { details = it }
    }

    val disabled = details?.isDisabled ?: pkg.isDisabled

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (pkg.isSystemApp) Icons.Default.Android else Icons.Default.Apps,
                    if (pkg.isSystemApp) AccentColors.Green else AccentColors.Blue,
                    size = 48.dp
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pkg.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        pkg.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val d = details
                    if (d == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        DetailRow("Version", listOf(d.versionName, d.versionCode.takeIf { it.isNotBlank() }?.let { "($it)" })
                            .filterNotNull().filter { it.isNotBlank() }.joinToString(" "))
                        DetailRow("Target SDK", d.targetSdk)
                        DetailRow("Installed", d.firstInstall)
                        DetailRow("Updated", d.lastUpdate)
                        DetailRow("Status", if (d.isDisabled) "Disabled" else "Enabled")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(Icons.Default.PlayArrow, "Launch", Modifier.weight(1f)) {
                    viewModel.launchApp(pkg.packageName); onDismiss()
                }
                ActionButton(Icons.Default.Stop, "Force stop", Modifier.weight(1f)) {
                    viewModel.forceStopPackage(pkg.packageName); onDismiss()
                }
                ActionButton(Icons.AutoMirrored.Filled.OpenInNew, "App info", Modifier.weight(1f)) {
                    viewModel.openAppSettingsOnDevice(pkg.packageName); onDismiss()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(Icons.Default.Download, "Extract APK", Modifier.weight(1f)) {
                    apkSaver.launch("${pkg.packageName}.apk")
                }
                ActionButton(
                    if (disabled) Icons.Default.CheckCircle else Icons.Default.Block,
                    if (disabled) "Enable" else "Disable",
                    Modifier.weight(1f)
                ) {
                    if (disabled) {
                        viewModel.setAppEnabled(pkg.packageName, true, showSystemApps)
                        onDismiss()
                        return@ActionButton
                    }
                    confirm = "disable"
                }
                ActionButton(Icons.Default.DeleteSweep, "Clear data", Modifier.weight(1f)) {
                    confirm = "clear"
                }
            }

            OutlinedButton(
                onClick = { showPermissions = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Permissions")
            }

            Button(
                onClick = { confirm = "uninstall" },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (pkg.isSystemApp) "Uninstall for current user" else "Uninstall")
            }
        }
    }

    if (showPermissions) {
        PermissionsDialog(pkg.packageName, viewModel) { showPermissions = false }
    }

    when (confirm) {
        "clear" -> ConfirmDialog(
            title = "Clear app data?",
            message = "All data for ${pkg.packageName} will be permanently deleted.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = { viewModel.clearPackageData(pkg.packageName); onDismiss() },
            onDismiss = { confirm = null }
        )
        "disable" -> ConfirmDialog(
            title = "Disable app?",
            message = "${pkg.packageName} will be hidden and stop running until it is enabled again.",
            confirmLabel = "Disable",
            destructive = true,
            onConfirm = { viewModel.setAppEnabled(pkg.packageName, false, showSystemApps); onDismiss() },
            onDismiss = { confirm = null }
        )
        "uninstall" -> ConfirmDialog(
            title = "Uninstall app?",
            message = if (pkg.isSystemApp)
                "${pkg.packageName} will be removed for the current user. It can be restored with " +
                    "'cmd package install-existing'."
            else "${pkg.packageName} and its data will be removed from the device.",
            confirmLabel = "Uninstall",
            destructive = true,
            onConfirm = { viewModel.uninstallPackage(pkg.packageName, pkg.isSystemApp, showSystemApps); onDismiss() },
            onDismiss = { confirm = null }
        )
    }
}

@Composable
private fun PermissionsDialog(packageName: String, viewModel: DeviceViewModel, onDismiss: () -> Unit) {
    var permissions by remember { mutableStateOf<List<com.adbcontroller.viewmodel.AppPermission>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        viewModel.getAppPermissions(packageName) { permissions = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions") },
        text = {
            val list = permissions
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> Text("This app has no runtime permissions.")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.name }) { perm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    perm.name.substringAfterLast('.').replace('_', ' ').lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    perm.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = perm.granted,
                                onCheckedChange = { grant ->
                                    viewModel.setAppPermission(packageName, perm.name, grant) { reload++ }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
