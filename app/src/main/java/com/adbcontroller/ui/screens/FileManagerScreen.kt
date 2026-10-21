package com.adbcontroller.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adbcontroller.ui.components.AccentColors
import com.adbcontroller.ui.components.ConfirmDialog
import com.adbcontroller.ui.components.IconBadge
import com.adbcontroller.ui.components.TextInputDialog
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.FileEntry

private val quickPaths = listOf(
    "/sdcard" to "Internal",
    "/sdcard/Download" to "Download",
    "/sdcard/DCIM" to "DCIM",
    "/sdcard/Pictures" to "Pictures",
    "/data/local/tmp" to "tmp",
    "/" to "/",
    "/data" to "data",
    "/system" to "System"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val fileError by viewModel.fileError.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    val rootMode by viewModel.rootFileMode.collectAsState()
    val clipboard = LocalClipboardManager.current

    var selected by remember { mutableStateOf<FileEntry?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<FileEntry?>(null) }

    val downloader = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val entry = pendingDownload
        if (uri != null && entry != null) viewModel.downloadFile(entry.path, uri)
        pendingDownload = null
    }
    val uploader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadFile(it) }
    }

    LaunchedEffect(Unit) {
        if (files.isEmpty()) viewModel.listFiles(currentPath)
    }

    BackHandler(enabled = currentPath != "/") { viewModel.navigateUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (rootMode) "Files (root)" else "Files")
                        Text(
                            currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (rootStatus.granted) {
                        IconToggleButton(
                            checked = rootMode,
                            onCheckedChange = { viewModel.setRootFileMode(it) }
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = if (rootMode) "Disable root mode" else "Enable root mode",
                                tint = if (rootMode) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.navigateUp() }, enabled = currentPath != "/") {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Parent folder")
                    }
                    IconButton(onClick = { viewModel.listFiles(currentPath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { uploader.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Upload, contentDescription = null) },
                text = { Text("Upload") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPaths) { (path, label) ->
                    FilterChip(
                        selected = currentPath == path,
                        onClick = { viewModel.listFiles(path) },
                        label = { Text(label) }
                    )
                }
            }

            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                fileError != null && files.isEmpty() -> EmptyState(
                    Icons.Default.Lock, "Can't open this folder", fileError ?: ""
                )
                !isLoading && files.isEmpty() -> EmptyState(
                    Icons.Default.FolderOpen, "Empty folder", "Nothing here yet"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(files, key = { it.path }) { file ->
                        val (icon, tint) = fileVisual(file)
                        ListItem(
                            modifier = Modifier.clickable {
                                if (file.isDirectory) viewModel.listFiles(file.path) else selected = file
                            },
                            headlineContent = {
                                Text(
                                    file.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        file.date,
                                        if (!file.isDirectory) DeviceViewModel.formatBytes(file.size) else null,
                                        if (file.isLink) "link" else null
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = { IconBadge(icon, tint) },
                            trailingContent = {
                                IconButton(onClick = { selected = file }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${file.name}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    selected?.let { file ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, tint) = fileVisual(file)
                    IconBadge(icon, tint, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(
                            listOf(file.permissions, file.date).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (file.isDirectory) {
                    SheetAction(Icons.Default.FolderOpen, "Open") {
                        selected = null
                        viewModel.listFiles(file.path)
                    }
                } else {
                    SheetAction(Icons.Default.Download, "Download to this phone") {
                        selected = null
                        pendingDownload = file
                        downloader.launch(file.name)
                    }
                    if (file.name.endsWith(".apk", ignoreCase = true)) {
                        SheetAction(Icons.Default.InstallMobile, "Install on device") {
                            selected = null
                            viewModel.installRemoteApk(file.path)
                        }
                    }
                }
                SheetAction(Icons.Default.ContentCopy, "Copy path") {
                    clipboard.setText(AnnotatedString(file.path))
                    selected = null
                }
                SheetAction(Icons.Default.DriveFileRenameOutline, "Rename") {
                    selected = null
                    renameTarget = file
                }
                SheetAction(Icons.Default.Delete, "Delete", MaterialTheme.colorScheme.error) {
                    selected = null
                    deleteTarget = file
                }
            }
        }
    }

    if (showNewFolder) {
        TextInputDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            onConfirm = { viewModel.createFolder(it.trim()) },
            onDismiss = { showNewFolder = false }
        )
    }

    renameTarget?.let { file ->
        TextInputDialog(
            title = "Rename",
            label = "New name",
            initial = file.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameFile(file, it.trim()) },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { file ->
        ConfirmDialog(
            title = "Delete ${if (file.isDirectory) "folder" else "file"}?",
            message = "\"${file.name}\" will be permanently deleted from the device" +
                if (file.isDirectory) ", including everything inside it." else ".",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteFile(file) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label, color = if (tint == Color.Unspecified) Color.Unspecified else tint) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fileVisual(file: FileEntry): Pair<ImageVector, Color> {
    if (file.isDirectory) return Icons.Default.Folder to AccentColors.Amber
    return when (file.name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> Icons.Default.Image to AccentColors.Pink
        "mp4", "mkv", "webm", "3gp", "mov", "avi" -> Icons.Default.Movie to AccentColors.Purple
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus" -> Icons.Default.AudioFile to AccentColors.Orange
        "apk", "apks", "xapk" -> Icons.Default.Android to AccentColors.Green
        "zip", "rar", "7z", "tar", "gz", "xz" -> Icons.Default.FolderZip to AccentColors.Slate
        "txt", "log", "md", "json", "xml", "csv", "prop", "conf" -> Icons.Default.Description to AccentColors.Blue
        "pdf" -> Icons.Default.PictureAsPdf to AccentColors.Red
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to AccentColors.Slate
    }
}
