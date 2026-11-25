package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.components.BottomNavTab
import com.example.ui.navigation.DevicesRoute
import com.example.ui.navigation.FilePickerRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.QrPairingRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.TransferSummaryRoute
import com.example.ui.navigation.TransfersRoute
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.FilePickerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.QrPairingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransferSummaryScreen
import com.example.ui.screens.TransfersScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianOnSurface
import com.example.ui.theme.ObsidianSurfaceContainerHigh
import com.example.ui.theme.WiFiShareTheme
import com.example.ui.viewmodel.FilePickerViewModel
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ObsidianBackground.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(ObsidianBackground.toArgb()),
        )
        setContent {
            WiFiShareTheme {
                WiFiShareAppRoot()
            }
        }
    }
}

@Composable
fun WiFiShareAppRoot(
    mainViewModel: MainViewModel = viewModel(),
    filePickerViewModel: FilePickerViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val shellState by mainViewModel.shellState.collectAsStateWithLifecycle()
    val pausedTransfers by mainViewModel.pausedTransfers.collectAsStateWithLifecycle()
    val devicesState by mainViewModel.devicesUiState.collectAsStateWithLifecycle()
    val transfersState by mainViewModel.transfersUiState.collectAsStateWithLifecycle()
    val qrState by mainViewModel.qrUiState.collectAsStateWithLifecycle()
    val summaryState by mainViewModel.summaryState.collectAsStateWithLifecycle()
    val statusBanner by mainViewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val filePickerState by filePickerViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val hotspotPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }

    val hotspotPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            mainViewModel.startHotspotAfterPermission()
        }
    }

    LaunchedEffect(statusBanner) {
        val message = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        mainViewModel.clearStatusBanner()
    }

    val navigateTab: (BottomNavTab) -> Unit = { tab ->
        val destination: Any = when (tab) {
            BottomNavTab.HOME -> HomeRoute
            BottomNavTab.TRANSFERS -> TransfersRoute
            BottomNavTab.DEVICES -> DevicesRoute
        }
        navController.navigate(destination) {
            popUpTo<HomeRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val requestHotspotIfNeeded: () -> Unit = {
        val hasPerm = ContextCompat.checkSelfPermission(context, hotspotPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            mainViewModel.startHotspotAfterPermission()
        } else {
            hotspotPermissionLauncher.launch(hotspotPermission)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    shellState = shellState,
                    pausedTransfers = pausedTransfers,
                    devicesState = devicesState,
                    onSendClick = {
                        navController.navigate(FilePickerRoute())
                    },
                    onReceiveClick = {
                        requestHotspotIfNeeded()
                        mainViewModel.toggleReceiveReady()
                    },
                    onResumeTransfer = { paused ->
                        mainViewModel.resumeTransfer(paused)
                        navigateTab(BottomNavTab.TRANSFERS)
                    },
                    onPairViaQrClick = {
                        navController.navigate(QrPairingRoute(initialShowMyQr = false))
                    },
                    onSettingsClick = {
                        navController.navigate(SettingsRoute)
                    },
                    onHotspotClick = {
                        requestHotspotIfNeeded()
                        navController.navigate(QrPairingRoute(initialShowMyQr = true))
                    },
                    onSendToDevice = { device ->
                        navController.navigate(FilePickerRoute(targetDeviceId = device.deviceId))
                    },
                    onPairDevice = { device ->
                        mainViewModel.pairDiscoveredDevice(device)
                    },
                    onNavigateTab = navigateTab,
                )
            }

            composable<FilePickerRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<FilePickerRoute>()
                LaunchedEffect(route.targetDeviceId) {
                    filePickerViewModel.setInitialTargetDevice(route.targetDeviceId)
                }
                FilePickerScreen(
                    uiState = filePickerState,
                    onBackClick = { navController.popBackStack() },
                    onToggleSearch = filePickerViewModel::toggleSearchVisibility,
                    onSearchQueryChange = filePickerViewModel::updateSearchQuery,
                    onSelectCategory = filePickerViewModel::selectCategory,
                    onToggleSelectAll = filePickerViewModel::toggleSelectAllVisible,
                    onToggleItem = filePickerViewModel::toggleItemSelection,
                    onAddDocuments = filePickerViewModel::addPickedUris,
                    onAddFolderTree = filePickerViewModel::addPickedFolderTree,
                    onAddVisualMedia = filePickerViewModel::addPickedUris,
                    onSelectTargetDevice = filePickerViewModel::selectTargetDevice,
                    onSendNow = { target ->
                        filePickerViewModel.startSending(target) {
                            navigateTab(BottomNavTab.TRANSFERS)
                        }
                    },
                    onPairViaQrNeeded = {
                        navController.navigate(QrPairingRoute(initialShowMyQr = false))
                    },
                )
            }

            composable<TransfersRoute> {
                TransfersScreen(
                    shellState = shellState,
                    uiState = transfersState,
                    onPauseTransfer = mainViewModel::pauseTransfer,
                    onResumeTransfer = mainViewModel::resumeTransfer,
                    onViewSummary = { sessionId ->
                        navController.navigate(TransferSummaryRoute(sessionId = sessionId))
                    },
                    onSendFilesClick = {
                        navController.navigate(FilePickerRoute())
                    },
                    onQrClick = {
                        navController.navigate(QrPairingRoute(initialShowMyQr = false))
                    },
                    onSettingsClick = {
                        navController.navigate(SettingsRoute)
                    },
                    onHotspotClick = {
                        requestHotspotIfNeeded()
                        navController.navigate(QrPairingRoute(initialShowMyQr = true))
                    },
                    onNavigateTab = navigateTab,
                )
            }

            composable<DevicesRoute> {
                DevicesScreen(
                    shellState = shellState,
                    uiState = devicesState,
                    onScanQrClick = {
                        navController.navigate(QrPairingRoute(initialShowMyQr = false))
                    },
                    onShowMyQrClick = {
                        navController.navigate(QrPairingRoute(initialShowMyQr = true))
                    },
                    onSendToDevice = { device ->
                        navController.navigate(FilePickerRoute(targetDeviceId = device.deviceId))
                    },
                    onPairDevice = mainViewModel::pairDiscoveredDevice,
                    onSettingsClick = {
                        navController.navigate(SettingsRoute)
                    },
                    onHotspotClick = {
                        requestHotspotIfNeeded()
                        navController.navigate(QrPairingRoute(initialShowMyQr = true))
                    },
                    onNavigateTab = navigateTab,
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    settings = shellState.settings,
                    pairedCount = devicesState.pairedDevices.size,
                    onBackClick = { navController.popBackStack() },
                    onUpdateDeviceName = mainViewModel::updateDeviceName,
                    onTogglePrefer5Ghz = mainViewModel::updatePrefer5Ghz,
                    onToggleSimultaneous = mainViewModel::updateSimultaneousTransfer,
                    onToggleAutoResume = mainViewModel::updateAutoResume,
                    onOpenPairedDevices = {
                        navController.popBackStack()
                        navigateTab(BottomNavTab.DEVICES)
                    },
                    onClearPairings = mainViewModel::clearAllPairings,
                )
            }

            composable<QrPairingRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<QrPairingRoute>()
                QrPairingScreen(
                    initialShowMyQr = route.initialShowMyQr,
                    hotspotState = shellState.hotspotState,
                    qrState = qrState,
                    onRefreshMyQr = mainViewModel::refreshLocalQrCode,
                    onQrDecoded = mainViewModel::onQrScanned,
                    onToggleFlash = mainViewModel::toggleQrFlash,
                    onResetScanState = mainViewModel::resetQrScanState,
                    onSendToPairedDevice = { device ->
                        navController.navigate(FilePickerRoute(targetDeviceId = device.deviceId)) {
                            popUpTo<HomeRoute>()
                        }
                    },
                    onBackClick = { navController.popBackStack() },
                )
            }

            composable<TransferSummaryRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<TransferSummaryRoute>()
                LaunchedEffect(route.sessionId) {
                    mainViewModel.loadSummary(route.sessionId)
                }
                TransferSummaryScreen(
                    summary = summaryState,
                    onSendMoreClick = {
                        navController.navigate(FilePickerRoute()) {
                            popUpTo<HomeRoute>()
                        }
                    },
                    onReturnHomeClick = {
                        navController.navigate(HomeRoute) {
                            popUpTo<HomeRoute> { inclusive = true }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(bottom = 84.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = ObsidianSurfaceContainerHigh,
                contentColor = ObsidianOnSurface,
                actionColor = EmeraldPrimary,
            )
        }
    }
}
