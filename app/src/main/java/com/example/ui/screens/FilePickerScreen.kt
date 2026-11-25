package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.FileCategory
import com.example.domain.model.NearbyDevice
import com.example.domain.model.SelectableFileItem
import com.example.ui.components.CircleIconButton
import com.example.ui.components.ContextualTopAppBar
import com.example.ui.theme.MonoTypography
import com.example.ui.theme.ObsidianSurfaceContainer
import com.example.ui.theme.ObsidianSurfaceContainerHigh
import com.example.ui.theme.ObsidianSurfaceContainerLow
import com.example.ui.viewmodel.FilePickerUiState
import com.example.util.FormatUtils

@Composable
fun FilePickerScreen(
    uiState: FilePickerUiState,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleItem: (String) -> Unit,
    onAddDocuments: (List<Uri>) -> Unit,
    onAddFolderTree: (Uri?) -> Unit,
    onAddVisualMedia: (List<Uri>) -> Unit,
    onSelectTargetDevice: (String) -> Unit,
    onSendNow: (NearbyDevice) -> Unit,
    onPairViaQrNeeded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTargetDialog by remember { mutableStateOf(false) }

    val openDocsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> onAddDocuments(uris) }

    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> onAddFolderTree(uri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> onAddVisualMedia(uris) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ContextualTopAppBar(
                title = "اختيار الملفات والمجلدات",
                subtitle = "جاهز للإرسال عبر نقطة الاتصال",
                onNavigateBack = onBackClick,
                trailingContent = {
                    CircleIconButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        onClick = onToggleSearch,
                        testTag = "file_picker_search_button",
                    )
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurfaceContainerLow),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "${uiState.selectedCount} عناصر محددة",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = FormatUtils.formatBytes(uiState.selectedTotalBytes),
                            style = MonoTypography.Small,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Button(
                        onClick = {
                            val target = uiState.selectedTarget
                            if (target != null) {
                                onSendNow(target)
                            } else {
                                showTargetDialog = true
                            }
                        },
                        enabled = uiState.selectedCount > 0 && !uiState.isSending,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("file_picker_send_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إرسال الآن (${uiState.selectedCount})",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AnimatedVisibility(visible = uiState.isSearchVisible) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = "ابحث عن اسم الملف أو المجلد...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = ObsidianSurfaceContainer,
                            unfocusedContainerColor = ObsidianSurfaceContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("file_picker_search_input"),
                    )
                }
            }

            // Real Device Storage Pickers (Play Policy Compliant SAF & Photo Picker)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DevicePickerChip(
                        icon = Icons.Outlined.NoteAdd,
                        label = "إضافة ملفات",
                        onClick = { openDocsLauncher.launch(arrayOf("*/*")) },
                        testTag = "pick_documents_button",
                    )
                    DevicePickerChip(
                        icon = Icons.Outlined.CreateNewFolder,
                        label = "إضافة مجلد",
                        onClick = { openTreeLauncher.launch(null) },
                        testTag = "pick_folder_button",
                    )
                    DevicePickerChip(
                        icon = Icons.Outlined.PhotoLibrary,
                        label = "الصور والفيديو",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        testTag = "pick_media_button",
                    )
                }
            }

            // Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        FileCategory.ALL,
                        FileCategory.FOLDER,
                        FileCategory.IMAGE,
                        FileCategory.VIDEO,
                        FileCategory.APP,
                    ).forEach { category ->
                        val selected = uiState.selectedCategory == category
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSelectCategory(category) }
                                .testTag("filter_chip_${category.name}"),
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else ObsidianSurfaceContainerHigh,
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                text = category.labelAr,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Path & Select All Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "التخزين الداخلي / WiFiShare",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (uiState.items.isNotEmpty()) {
                        TextButton(
                            onClick = onToggleSelectAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (uiState.allVisibleSelected) "إلغاء التحديد" else "تحديد الكل",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (uiState.items.isEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "اختر ملفات أو مجلدات من جهازك",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "استخدم الأزرار أعلاه لإضافة ملفات أو مجلدات أو وسائط للإرسال الفوري بأقصى سرعة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(uiState.items, key = { it.id }) { item ->
                    SelectableFileRow(
                        item = item,
                        onClick = { onToggleItem(item.id) },
                    )
                }
            }
        }
    }

    if (showTargetDialog) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            containerColor = ObsidianSurfaceContainerHigh,
            title = {
                Text(
                    text = "اختر الجهاز المستلم",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                if (uiState.availableTargets.isEmpty()) {
                    Text(
                        text = "لا يوجد جهاز مرتبط حالياً. امسح رمز QR لربط الجهاز الآخر لمرة واحدة وبدء الإرسال.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.availableTargets.forEach { device ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectTargetDevice(device.deviceId)
                                        showTargetDialog = false
                                        onSendNow(device)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Text(
                                    text = device.deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (uiState.availableTargets.isEmpty()) {
                    Button(
                        onClick = {
                            showTargetDialog = false
                            onPairViaQrNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("مسح رمز QR")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDialog = false }) {
                    Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun DevicePickerChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SelectableFileRow(
    item: SelectableFileItem,
    onClick: () -> Unit,
) {
    val bgColor = if (item.isSelected) ObsidianSurfaceContainerHigh else ObsidianSurfaceContainer
    val borderColor = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("file_row_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (item.isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else ObsidianSurfaceContainerHigh,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = fileCategoryIcon(item.category),
                        contentDescription = null,
                        tint = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = if (item.isFolder) {
                        "${item.fileCount} ملف • ${FormatUtils.formatBytes(item.sizeBytes)}"
                    } else {
                        "${item.extensionLabel} • ${FormatUtils.formatBytes(item.sizeBytes)}"
                    }
                    Text(
                        text = subtitle,
                        style = MonoTypography.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (item.isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (item.isSelected) "محدد" else "غير محدد",
                tint = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun fileCategoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Filled.Folder
    FileCategory.IMAGE -> Icons.Outlined.Image
    FileCategory.VIDEO -> Icons.Outlined.Movie
    FileCategory.APP -> Icons.Outlined.Android
    FileCategory.DOCUMENT, FileCategory.ALL -> Icons.Outlined.Description
}
