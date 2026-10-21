package com.adbcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adbcontroller.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val output by viewModel.shellOutput.collectAsState()
    val rootShell by viewModel.rootShell.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(output) {
        if (output.isNotBlank()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (rootShell) "Root Shell" else "Shell Terminal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (rootStatus.granted) {
                        FilterChip(
                            selected = rootShell,
                            onClick = { viewModel.setRootShell(!rootShell) },
                            label = { Text("root", fontFamily = FontFamily.Monospace) },
                            leadingIcon = {
                                Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color(0xFF90CAF9),
                                iconColor = Color(0xFF90CAF9),
                                selectedContainerColor = Color(0xFF3A1F1F),
                                selectedLabelColor = Color(0xFFFF8A80),
                                selectedLeadingIconColor = Color(0xFFFF8A80)
                            )
                        )
                    }
                    IconButton(onClick = { viewModel.clearShellOutput() }) {
                        Icon(Icons.Default.ClearAll, "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color(0xFF4CAF50),
                    navigationIconContentColor = Color(0xFF4CAF50)
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF1A1A2E),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (rootShell) "#" else "$",
                        color = if (rootShell) Color(0xFFFF8A80) else Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFFE0E0E0)
                        ),
                        placeholder = {
                            Text(
                                "Enter command...",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF666666)
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (command.isNotBlank()) {
                                    commandHistory.add(0, command)
                                    historyIndex = -1
                                    viewModel.executeShellCommand(command)
                                    command = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color(0xFF333333),
                            cursorColor = Color(0xFF4CAF50)
                        )
                    )
                    IconButton(
                        onClick = {
                            if (command.isNotBlank()) {
                                commandHistory.add(0, command)
                                historyIndex = -1
                                viewModel.executeShellCommand(command)
                                command = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Send,
                            "Execute",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D0D1A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickCommandChip("ls -la") { viewModel.executeShellCommand(it) }
                QuickCommandChip("pwd") { viewModel.executeShellCommand(it) }
                QuickCommandChip("whoami") { viewModel.executeShellCommand(it) }
                QuickCommandChip("df -h") { viewModel.executeShellCommand(it) }
                QuickCommandChip("top -n 1") { viewModel.executeShellCommand(it) }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                state = listState,
                reverseLayout = true
            ) {
                item {
                    Text(
                        text = output.ifEmpty { "Shell ready. Type a command below.\n" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickCommandChip(command: String, onExecute: (String) -> Unit) {
    SuggestionChip(
        onClick = { onExecute(command) },
        label = {
            Text(
                command,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = Color(0xFF1A1A2E),
            labelColor = Color(0xFF90CAF9)
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled = true,
            borderColor = Color(0xFF333355)
        )
    )
}
