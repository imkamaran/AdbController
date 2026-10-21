package com.adbcontroller.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adbcontroller.ui.screens.*
import com.adbcontroller.viewmodel.ConnectionState
import com.adbcontroller.viewmodel.DeviceViewModel
import com.adbcontroller.viewmodel.TransferProgress
import kotlinx.coroutines.flow.collectLatest

object Routes {
    const val CONNECTION = "connection"
    const val DASHBOARD = "dashboard"
    const val DEVICE_INFO = "device_info"
    const val SHELL = "shell"
    const val FILES = "files"
    const val PACKAGES = "packages"
    const val LOGCAT = "logcat"
    const val SCREENSHOT = "screenshot"
    const val PROCESSES = "processes"
    const val NETWORK = "network"
    const val BATTERY = "battery"
    const val PROPERTIES = "properties"
    const val WIFI_ADB = "wifi_adb"
    const val PAIRING_CODE = "pairing_code"
    const val QR_PAIRING = "qr_pairing"
    const val REMOTE = "remote"
    const val QUICK_SETTINGS = "quick_settings"
    const val SETTINGS = "settings"
    const val ROOT = "root"
    const val KERNEL_LOG = "kernel_log"
    const val PERFORMANCE = "performance"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"

    val offline = setOf(CONNECTION, PAIRING_CODE, QR_PAIRING, SETTINGS, ABOUT)
}

@Composable
fun AppNavigation(viewModel: DeviceViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val connectionState by viewModel.connectionState.collectAsState()
    val transfer by viewModel.transfer.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(connectionState) {
        val route = navController.currentDestination?.route ?: return@LaunchedEffect
        val online = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting
        if (!online && route !in Routes.offline) {
            navController.navigate(Routes.CONNECTION) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val back: () -> Unit = { navController.popBackStack() }
    val openDashboard: () -> Unit = {
        navController.navigate(Routes.DASHBOARD) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.CONNECTION,
            enterTransition = { slideInHorizontally(initialOffsetX = { it / 6 }) + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 6 }) + fadeOut() }
        ) {
            composable(Routes.CONNECTION) {
                ConnectionScreen(
                    viewModel = viewModel,
                    onConnected = {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.CONNECTION) { inclusive = true }
                        }
                    },
                    onNavigateToPairingCode = { navController.navigate(Routes.PAIRING_CODE) },
                    onNavigateToQrPairing = { navController.navigate(Routes.QR_PAIRING) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToAbout = { navController.navigate(Routes.ABOUT) }
                )
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigate = { route -> navController.navigate(route) },
                    onDisconnect = { viewModel.disconnect() }
                )
            }
            composable(Routes.DEVICE_INFO) { DeviceInfoScreen(viewModel, back) }
            composable(Routes.SHELL) { ShellScreen(viewModel, back) }
            composable(Routes.FILES) { FileManagerScreen(viewModel, back) }
            composable(Routes.PACKAGES) { PackageManagerScreen(viewModel, back) }
            composable(Routes.LOGCAT) { LogcatScreen(viewModel, back) }
            composable(Routes.SCREENSHOT) { ScreenshotScreen(viewModel, back) }
            composable(Routes.PROCESSES) { ProcessesScreen(viewModel, back) }
            composable(Routes.NETWORK) { NetworkScreen(viewModel, back) }
            composable(Routes.BATTERY) { BatteryScreen(viewModel, back) }
            composable(Routes.PROPERTIES) { PropertiesScreen(viewModel, back) }
            composable(Routes.WIFI_ADB) { WifiAdbScreen(viewModel, back) }
            composable(Routes.PAIRING_CODE) {
                PairingCodeScreen(viewModel, back, onConnected = { openDashboard() })
            }
            composable(Routes.QR_PAIRING) {
                QrPairingScreen(viewModel, back, onConnected = { openDashboard() })
            }
            composable(Routes.REMOTE) { RemoteControlScreen(viewModel, back) }
            composable(Routes.QUICK_SETTINGS) { QuickSettingsScreen(viewModel, back) }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel, back, onOpenAbout = { navController.navigate(Routes.ABOUT) })
            }
            composable(Routes.ABOUT) { AboutScreen(back) }
            composable(Routes.ROOT) {
                RootScreen(viewModel, back, onNavigate = { route -> navController.navigate(route) })
            }
            composable(Routes.KERNEL_LOG) { KernelLogScreen(viewModel, back) }
            composable(Routes.PERFORMANCE) { PerformanceScreen(viewModel, back) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(viewModel, back) }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = transfer != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                transfer?.let { TransferCard(it) }
            }
            SnackbarHost(snackbarHostState)
        }
    }
}

@Composable
private fun TransferCard(transfer: TransferProgress) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inversePrimary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    transfer.label,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                transfer.progress?.let {
                    Text(
                        "${(it * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val progress = transfer.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            }
        }
    }
}
