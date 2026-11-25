package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.network.QrCodeUtils
import com.example.domain.model.HotspotState
import com.example.domain.model.NearbyDevice
import com.example.ui.components.CircleIconButton
import com.example.ui.components.ContextualTopAppBar
import com.example.ui.components.VerifiedStatusBadge
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceContainerHigh
import com.example.ui.theme.ObsidianSurfaceContainerLowest
import com.example.ui.viewmodel.QrPairingUiState
import java.util.concurrent.Executors

@Composable
fun QrPairingScreen(
    initialShowMyQr: Boolean,
    hotspotState: HotspotState,
    qrState: QrPairingUiState,
    onRefreshMyQr: () -> Unit,
    onQrDecoded: (String) -> Unit,
    onToggleFlash: () -> Unit,
    onResetScanState: () -> Unit,
    onSendToPairedDevice: (NearbyDevice) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showMyQrMode by remember { mutableStateOf(initialShowMyQr) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        onResetScanState()
        onRefreshMyQr()
        if (!initialShowMyQr && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(showMyQrMode) {
        if (showMyQrMode) {
            onRefreshMyQr()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ContextualTopAppBar(
                title = "الربط السريع عبر QR",
                subtitle = "اتصال مباشر بنقطة الاتصال",
                onNavigateBack = onBackClick,
                trailingContent = {
                    if (!showMyQrMode) {
                        CircleIconButton(
                            icon = if (qrState.isFlashEnabled) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            contentDescription = stringResource(R.string.cd_flash),
                            onClick = onToggleFlash,
                            tint = if (qrState.isFlashEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            testTag = "qr_flash_button",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mode Switcher
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                color = ObsidianSurfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModeTabButton(
                        label = "مسح رمز QR",
                        selected = !showMyQrMode,
                        onClick = { showMyQrMode = false },
                        testTag = "qr_tab_scan",
                        modifier = Modifier.weight(1f),
                    )
                    ModeTabButton(
                        label = "عرض رمزي",
                        selected = showMyQrMode,
                        onClick = { showMyQrMode = true },
                        testTag = "qr_tab_show",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Viewfinder or My QR Bitmap
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 280.dp)
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = ObsidianSurfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qrState.pairedSuccessDevice != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "تم الربط",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp),
                                )
                                Text(
                                    text = "تم ربط ${qrState.pairedSuccessDevice.deviceName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Button(
                                    onClick = { onSendToPairedDevice(qrState.pairedSuccessDevice) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Text("إرسال ملفات الآن", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        } else if (showMyQrMode) {
                            val bmp = qrState.myQrBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "رمز QR الخاص بهذا الجهاز",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp)),
                                )
                            } else {
                                Text(
                                    text = "جاري إنشاء الرمز...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else if (hasCameraPermission) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                            ) {
                                CameraQrPreview(
                                    flashEnabled = qrState.isFlashEnabled,
                                    onQrDecoded = onQrDecoded,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                EmeraldCornerBrackets(modifier = Modifier.fillMaxSize())
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                EmeraldCornerBrackets(modifier = Modifier.fillMaxSize())
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp),
                                    )
                                    Text(
                                        text = "مطلوب إذن الكاميرا لمسح الرمز",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                    Button(
                                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        modifier = Modifier.testTag("grant_camera_button"),
                                    ) {
                                        Text("تفعيل الكاميرا", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = when {
                        qrState.scanErrorMessage != null -> qrState.scanErrorMessage
                        showMyQrMode -> "اطلب من الجهاز الآخر مسح هذا الرمز للربط المباشر"
                        else -> "وجّه الكاميرا نحو رمز QR في الجهاز الآخر"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (qrState.scanErrorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                VerifiedStatusBadge(label = "سيتم حفظ الارتباط تلقائياً لمرة واحدة")
            }

            // Hotspot Direct Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WifiTethering,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "نقطة الاتصال المحلية",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${hotspotState.ssid} • ${hotspotState.bandLabel}",
                                style = MonoTypography.Small,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showMyQrMode = !showMyQrMode },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(
                            text = if (showMyQrMode) "مسح الرمز" else "عرض رمزي",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EmeraldCornerBrackets(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val cornerLen = 28.dp.toPx()
        val pad = strokeWidth / 2f
        val w = size.width
        val h = size.height

        // Top-Left
        drawLine(EmeraldPrimary, Offset(pad, pad), Offset(cornerLen, pad), strokeWidth, StrokeCap.Round)
        drawLine(EmeraldPrimary, Offset(pad, pad), Offset(pad, cornerLen), strokeWidth, StrokeCap.Round)
        // Top-Right
        drawLine(EmeraldPrimary, Offset(w - cornerLen, pad), Offset(w - pad, pad), strokeWidth, StrokeCap.Round)
        drawLine(EmeraldPrimary, Offset(w - pad, pad), Offset(w - pad, cornerLen), strokeWidth, StrokeCap.Round)
        // Bottom-Left
        drawLine(EmeraldPrimary, Offset(pad, h - pad), Offset(cornerLen, h - pad), strokeWidth, StrokeCap.Round)
        drawLine(EmeraldPrimary, Offset(pad, h - cornerLen), Offset(pad, h - pad), strokeWidth, StrokeCap.Round)
        // Bottom-Right
        drawLine(EmeraldPrimary, Offset(w - cornerLen, h - pad), Offset(w - pad, h - pad), strokeWidth, StrokeCap.Round)
        drawLine(EmeraldPrimary, Offset(w - pad, h - cornerLen), Offset(w - pad, h - pad), strokeWidth, StrokeCap.Round)
        // Subtle inner guide
        drawRoundRect(
            color = EmeraldPrimary.copy(alpha = 0.14f),
            topLeft = Offset(pad, pad),
            size = Size(w - strokeWidth, h - strokeWidth),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun CameraQrPreview(
    flashEnabled: Boolean,
    onQrDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashEnabled, boundCamera) {
        runCatching {
            boundCamera?.cameraControl?.enableTorch(flashEnabled)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                try {
                                    val text = QrCodeUtils.decodeQrFromImageProxy(imageProxy)
                                    if (!text.isNullOrBlank()) {
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            onQrDecoded(text)
                                        }
                                    }
                                } finally {
                                    imageProxy.close()
                                }
                            }
                        }
                    runCatching {
                        cameraProvider.unbindAll()
                        boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer,
                        )
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}
