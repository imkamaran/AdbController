package com.adbcontroller.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.adbcontroller.ui.components.TextInputDialog
import com.adbcontroller.viewmodel.DeviceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object KeyCodes {
    const val HOME = 3
    const val BACK = 4
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val POWER = 26
    const val ENTER = 66
    const val DEL = 67
    const val MENU = 82
    const val MUTE = 164
    const val APP_SWITCH = 187
    const val SLEEP = 223
    const val WAKEUP = 224
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var live by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var frameTimeMs by remember { mutableLongStateOf(0L) }
    var captureFailed by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(live, refreshTick) {
        do {
            val start = System.currentTimeMillis()
            val bytes = viewModel.captureScreen()
            val bitmap = bytes?.let {
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            if (bitmap != null) {
                frame = bitmap
                captureFailed = false
            } else {
                captureFailed = true
            }
            frameTimeMs = System.currentTimeMillis() - start
            if (live) delay(if (captureFailed) 2000L else 150L)
        } while (live && isActive)
    }

    fun refreshSoon() {
        if (!live) scope.launch {
            delay(600)
            refreshTick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Remote Control")
                        Text(
                            when {
                                captureFailed -> "Screen capture unavailable"
                                live -> "Live · ${if (frameTimeMs > 0) "${frameTimeMs} ms/frame" else "starting…"}"
                                else -> "Paused · tap refresh to update"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { live = !live }) {
                        Icon(
                            if (live) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (live) "Pause live view" else "Resume live view"
                        )
                    }
                    if (!live) {
                        IconButton(onClick = { refreshTick++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Open URL…") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = { menuOpen = false; showUrlDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Notifications") },
                                leadingIcon = { Icon(Icons.Default.Notifications, null) },
                                onClick = { menuOpen = false; viewModel.expandNotifications(); refreshSoon() }
                            )
                            DropdownMenuItem(
                                text = { Text("Quick settings panel") },
                                leadingIcon = { Icon(Icons.Default.Tune, null) },
                                onClick = { menuOpen = false; viewModel.expandQuickSettingsPanel(); refreshSoon() }
                            )
                            DropdownMenuItem(
                                text = { Text("Collapse status bar") },
                                leadingIcon = { Icon(Icons.Default.ExpandLess, null) },
                                onClick = { menuOpen = false; viewModel.collapseStatusBar(); refreshSoon() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Wake screen") },
                                leadingIcon = { Icon(Icons.Default.LightMode, null) },
                                onClick = { menuOpen = false; viewModel.sendKey(KeyCodes.WAKEUP); refreshSoon() }
                            )
                            DropdownMenuItem(
                                text = { Text("Sleep screen") },
                                leadingIcon = { Icon(Icons.Default.Bedtime, null) },
                                onClick = { menuOpen = false; viewModel.sendKey(KeyCodes.SLEEP); refreshSoon() }
                            )
                            DropdownMenuItem(
                                text = { Text("Unlock (no PIN)") },
                                leadingIcon = { Icon(Icons.Default.LockOpen, null) },
                                onClick = { menuOpen = false; viewModel.sendKey(KeyCodes.MENU); refreshSoon() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = frame
                if (bitmap == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (captureFailed) {
                            Icon(
                                Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Couldn't capture the screen", color = MaterialTheme.colorScheme.error)
                        } else {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading screen…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    DeviceScreen(
                        bitmap = bitmap,
                        onTap = { x, y -> viewModel.tap(x, y); refreshSoon() },
                        onLongPress = { x, y -> viewModel.longPress(x, y); refreshSoon() },
                        onSwipe = { x1, y1, x2, y2, d -> viewModel.swipe(x1, y1, x2, y2, d); refreshSoon() }
                    )
                }
            }

            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NavKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { viewModel.sendKey(KeyCodes.BACK); refreshSoon() }
                        NavKey(Icons.Default.Circle, "Home") { viewModel.sendKey(KeyCodes.HOME); refreshSoon() }
                        NavKey(Icons.Default.CropSquare, "Recent apps") { viewModel.sendKey(KeyCodes.APP_SWITCH); refreshSoon() }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        SmallKey(Icons.AutoMirrored.Filled.VolumeDown, "Volume down") { viewModel.sendKey(KeyCodes.VOLUME_DOWN) }
                        SmallKey(Icons.AutoMirrored.Filled.VolumeUp, "Volume up") { viewModel.sendKey(KeyCodes.VOLUME_UP) }
                        SmallKey(Icons.AutoMirrored.Filled.VolumeOff, "Mute") { viewModel.sendKey(KeyCodes.MUTE) }
                        SmallKey(Icons.Default.PowerSettingsNew, "Power") { viewModel.sendKey(KeyCodes.POWER); refreshSoon() }
                        SmallKey(Icons.Default.Notifications, "Notifications") { viewModel.expandNotifications(); refreshSoon() }
                        SmallKey(Icons.Default.Backspace, "Delete") { viewModel.sendKey(KeyCodes.DEL); refreshSoon() }
                        SmallKey(Icons.AutoMirrored.Filled.KeyboardReturn, "Enter") { viewModel.sendKey(KeyCodes.ENTER); refreshSoon() }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type text on the device") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.inputText(text); text = ""; refreshSoon() },
                                enabled = text.isNotEmpty()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send text")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            viewModel.inputText(text)
                            text = ""
                            refreshSoon()
                        })
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        TextInputDialog(
            title = "Open URL on device",
            label = "URL",
            initial = "https://",
            confirmLabel = "Open",
            onConfirm = { viewModel.openUrl(it.trim()); refreshSoon() },
            onDismiss = { showUrlDialog = false }
        )
    }
}

@Composable
private fun DeviceScreen(
    bitmap: Bitmap,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    onSwipe: (Int, Int, Int, Int, Long) -> Unit
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val deviceWidth = bitmap.width
    val deviceHeight = bitmap.height
    val ratio = deviceWidth.toFloat() / deviceHeight

    fun toDevice(o: Offset): Pair<Int, Int> {
        if (viewSize.width == 0 || viewSize.height == 0) return 0 to 0
        val x = (o.x / viewSize.width * deviceWidth).toInt().coerceIn(0, deviceWidth - 1)
        val y = (o.y / viewSize.height * deviceHeight).toInt().coerceIn(0, deviceHeight - 1)
        return x to y
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Device screen. Tap, long-press or swipe to interact.",
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onSizeChanged { viewSize = it }
            .pointerInput(deviceWidth, deviceHeight) {
                detectTapGestures(
                    onTap = { o -> val (x, y) = toDevice(o); onTap(x, y) },
                    onLongPress = { o -> val (x, y) = toDevice(o); onLongPress(x, y) }
                )
            }
            .pointerInput(deviceWidth, deviceHeight) {
                var start = Offset.Zero
                var current = Offset.Zero
                var startTime = 0L
                detectDragGestures(
                    onDragStart = { o ->
                        start = o
                        current = o
                        startTime = System.currentTimeMillis()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        current += amount
                    },
                    onDragEnd = {
                        val (x1, y1) = toDevice(start)
                        val (x2, y2) = toDevice(current)
                        val duration = (System.currentTimeMillis() - startTime).coerceIn(80L, 1500L)
                        onSwipe(x1, y1, x2, y2, duration)
                    }
                )
            }
    )
}

@Composable
private fun NavKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(width = 72.dp, height = 48.dp)) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun SmallKey(icon: ImageVector, description: String, onClick: () -> Unit) {
    OutlinedIconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}
