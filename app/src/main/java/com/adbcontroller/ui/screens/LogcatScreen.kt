package com.adbcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.LogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val logEntries by viewModel.logEntries.collectAsState()
    var isRunning by remember { mutableStateOf(false) }
    var filterLevel by remember { mutableStateOf("V") }
    var searchTag by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val filteredEntries = logEntries.filter { entry ->
        val levelPriority = mapOf("V" to 0, "D" to 1, "I" to 2, "W" to 3, "E" to 4, "F" to 5)
        val entryLevel = levelPriority[entry.level] ?: 0
        val filterLevelPriority = levelPriority[filterLevel] ?: 0
        entryLevel >= filterLevelPriority &&
                (searchTag.isBlank() || entry.tag.contains(searchTag, ignoreCase = true) ||
                        entry.message.contains(searchTag, ignoreCase = true))
    }

    LaunchedEffect(filteredEntries.size) {
        if (autoScroll && filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logcat") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopLogcat()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.PanTool,
                            "Auto-scroll",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.clearLogcat() }) {
                        Icon(Icons.Default.ClearAll, "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color(0xFFE0E0E0),
                    navigationIconContentColor = Color(0xFFE0E0E0)
                )
            )
        },
        bottomBar = {
            Surface(color = Color(0xFF1A1A2E)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("V", "D", "I", "W", "E", "F").forEach { level ->
                            FilterChip(
                                selected = filterLevel == level,
                                onClick = { filterLevel = level },
                                label = { Text(level, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF252540),
                                    selectedContainerColor = logLevelColor(level).copy(alpha = 0.3f),
                                    labelColor = Color(0xFFBBBBBB),
                                    selectedLabelColor = logLevelColor(level)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchTag,
                            onValueChange = { searchTag = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Filter by tag/message...", fontSize = 12.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFE0E0E0)
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color(0xFF333333)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                if (isRunning) {
                                    viewModel.stopLogcat()
                                    isRunning = false
                                } else {
                                    viewModel.startLogcat()
                                    isRunning = true
                                }
                            }
                        ) {
                            Icon(
                                if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRunning) "Stop" else "Start")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D0D1A))
        ) {
            if (filteredEntries.isEmpty()) {
                Text(
                    if (isRunning) "Waiting for logs..." else "Press Start to begin capturing logs",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF666666)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(filteredEntries) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }

            Text(
                "${filteredEntries.size} entries",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xAA000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color(0xFF888888),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        Text(
            entry.level,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = logLevelColor(entry.level),
            modifier = Modifier.width(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            entry.time,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF666666),
            modifier = Modifier.width(130.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            entry.tag,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF90CAF9),
            modifier = Modifier.widthIn(max = 160.dp),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFFBBBBBB),
            maxLines = 3
        )
    }
}

private fun logLevelColor(level: String): Color = when (level) {
    "V" -> Color(0xFF888888)
    "D" -> Color(0xFF2196F3)
    "I" -> Color(0xFF4CAF50)
    "W" -> Color(0xFFFF9800)
    "E" -> Color(0xFFF44336)
    "F" -> Color(0xFFFF0000)
    else -> Color(0xFFBBBBBB)
}
