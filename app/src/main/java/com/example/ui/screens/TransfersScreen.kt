package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.TransferEntity
import com.example.domain.model.FileCategory
import com.example.domain.model.TransferStatus
import com.example.ui.components.BottomNavTab
import com.example.ui.components.CircleIconButton
import com.example.ui.components.EmeraldProgressBar
import com.example.ui.components.MainTopAppBar
import com.example.ui.components.ResumableTransferCard
import com.example.ui.components.VerifiedStatusBadge
import com.example.ui.components.WiFiShareBottomBar
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldWarning
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceContainerHigh
import com.example.ui.viewmodel.MainShellUiState
import com.example.ui.viewmodel.TransfersUiState
import com.example.util.FormatUtils

@Composable
fun TransfersScreen(
    shellState: MainShellUiState,
    uiState: TransfersUiState,
    onPauseTransfer: (String) -> Unit,
    onResumeTransfer: (TransferEntity) -> Unit,
    onViewSummary: (String) -> Unit,
    onSendFilesClick: () -> Unit,
    onQrClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHotspotClick: () -> Unit,
    onNavigateTab: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MainTopAppBar(
                hotspotState = shellState.hotspotState,
                onQrClick = onQrClick,
                onSettingsClick = onSettingsClick,
                onHotspotClick = onHotspotClick,
            )
        },
        bottomBar = {
            WiFiShareBottomBar(
                currentTab = BottomNavTab.TRANSFERS,
                activeTransferCount = shellState.activeTransferCount,
                onTabSelected = onNavigateTab,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Live Simultaneous Speed & Connection Summary Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = ObsidianSurfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Smartphone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = uiState.activePeer?.deviceName ?: "في انتظار اتصال جهاز",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (uiState.activePeer != null) {
                                VerifiedStatusBadge(label = "مرتبط لمرة واحدة")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SpeedMetricCard(
                                label = "سرعة الإرسال",
                                speedBps = uiState.outgoingSpeedBps,
                                isOutgoing = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            SpeedMetricCard(
                                label = "سرعة الاستقبال",
                                speedBps = uiState.incomingSpeedBps,
                                isOutgoing = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "النقل المتزامن نشط في الاتجاهين",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (uiState.estimatedRemainingSeconds > 0L) {
                                    "متبقي ${FormatUtils.formatDurationMmSs(uiState.estimatedRemainingSeconds)}"
                                } else {
                                    "جاهز"
                                },
                                style = MonoTypography.Small,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (uiState.hasCompletedSession) {
                item {
                    Button(
                        onClick = { onViewSummary(uiState.latestCompletedSessionId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("view_transfer_summary_button"),
                    ) {
                        Text(
                            text = "عرض ملخص النقل المكتمل",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            if (uiState.outgoingTransfers.isEmpty() && uiState.incomingTransfers.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "لا توجد عمليات نقل نشطة حالياً",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "اختر ملفات أو مجلدات لبدء الإرسال والاستقبال المتزامن عبر نقطة الاتصال.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onSendFilesClick,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            ) {
                                Text("اختيار ملفات للإرسال", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Outgoing Section
            if (uiState.outgoingTransfers.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "قيد الإرسال",
                        countText = "${uiState.outgoingTransfers.size} ملفات",
                        countColor = MaterialTheme.colorScheme.primary,
                    )
                }
                items(uiState.outgoingTransfers, key = { it.id }) { transfer ->
                    TransferItemRow(
                        transfer = transfer,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onPauseClick = { onPauseTransfer(transfer.id) },
                        onResumeClick = { onResumeTransfer(transfer) },
                    )
                }
            }

            // Incoming Section
            if (uiState.incomingTransfers.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "قيد الاستقبال",
                        countText = "${uiState.incomingTransfers.size} ملفات",
                        countColor = MaterialTheme.colorScheme.secondary,
                    )
                }
                items(uiState.incomingTransfers, key = { it.id }) { transfer ->
                    TransferItemRow(
                        transfer = transfer,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onPauseClick = { onPauseTransfer(transfer.id) },
                        onResumeClick = { onResumeTransfer(transfer) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedMetricCard(
    label: String,
    speedBps: Long,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (isOutgoing) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
            Text(
                text = FormatUtils.formatSpeedMbps(speedBps),
                style = MonoTypography.Large,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    countText: String,
    countColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = countText,
            style = MonoTypography.Small,
            color = countColor,
        )
    }
}

@Composable
private fun TransferItemRow(
    transfer: TransferEntity,
    accentColor: Color,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
) {
    when (transfer.status) {
        TransferStatus.PAUSED.name -> {
            ResumableTransferCard(
                transfer = transfer,
                onResumeClick = onResumeClick,
                accentColor = EmeraldWarning,
                subtitleHint = "جاهز للاستكمال من نقطة التوقف",
            )
        }

        TransferStatus.COMPLETED.name -> {
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
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transfer.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${FormatUtils.formatBytes(transfer.totalBytes)} • مكتمل",
                                style = MonoTypography.Small,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "مكتمل",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        else -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    imageVector = when {
                                        transfer.isFolder -> Icons.Outlined.Folder
                                        transfer.category == FileCategory.VIDEO.name -> Icons.Outlined.Movie
                                        else -> Icons.Outlined.Description
                                    },
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = transfer.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${FormatUtils.formatBytes(transfer.transferredBytes)} / ${FormatUtils.formatBytes(transfer.totalBytes)}",
                                    style = MonoTypography.Small,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${transfer.progressPercent}%",
                                style = MonoTypography.Small,
                                color = accentColor,
                            )
                            CircleIconButton(
                                icon = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.cd_pause),
                                onClick = onPauseClick,
                                testTag = "pause_transfer_${transfer.id}",
                            )
                        }
                    }

                    EmeraldProgressBar(
                        progress = transfer.progressFraction,
                        fillColor = accentColor,
                    )
                }
            }
        }
    }
}
