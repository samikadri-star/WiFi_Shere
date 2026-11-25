package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WiFiShareApp
import com.example.domain.model.FileCategory
import com.example.domain.model.NearbyDevice
import com.example.domain.model.SelectableFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FilePickerUiState(
    val items: List<SelectableFileItem> = emptyList(),
    val selectedCategory: FileCategory = FileCategory.ALL,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val selectedCount: Int = 0,
    val selectedTotalBytes: Long = 0L,
    val allVisibleSelected: Boolean = false,
    val availableTargets: List<NearbyDevice> = emptyList(),
    val selectedTarget: NearbyDevice? = null,
    val isSending: Boolean = false,
)

class FilePickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as WiFiShareApp).repository

    private val _allItems = MutableStateFlow<List<SelectableFileItem>>(emptyList())
    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchVisible = MutableStateFlow(false)
    private val _selectedTargetId = MutableStateFlow<String?>(null)
    private val _isSending = MutableStateFlow(false)

    val uiState: StateFlow<FilePickerUiState> = combine(
        _allItems,
        _selectedCategory,
        _searchQuery,
        _isSearchVisible,
        repository.pairedDevicesFlow,
        _selectedTargetId,
        _isSending,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val items = values[0] as List<SelectableFileItem>
        val category = values[1] as FileCategory
        val query = values[2] as String
        val searchVisible = values[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val paired = values[4] as List<NearbyDevice>
        val targetId = values[5] as String?
        val sending = values[6] as Boolean

        val filtered = items.filter { item ->
            val matchesCat = category == FileCategory.ALL || item.category == category
            val matchesQuery = query.isBlank() || item.name.contains(query, ignoreCase = true)
            matchesCat && matchesQuery
        }
        val selectedItems = items.filter { it.isSelected }
        val target = paired.firstOrNull { it.deviceId == targetId } ?: paired.firstOrNull()

        FilePickerUiState(
            items = filtered,
            selectedCategory = category,
            searchQuery = query,
            isSearchVisible = searchVisible,
            selectedCount = selectedItems.size,
            selectedTotalBytes = selectedItems.sumOf { it.sizeBytes },
            allVisibleSelected = filtered.isNotEmpty() && filtered.all { it.isSelected },
            availableTargets = paired,
            selectedTarget = target,
            isSending = sending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilePickerUiState(),
    )

    init {
        refreshLocalDirectory()
    }

    fun setInitialTargetDevice(deviceId: String?) {
        if (!deviceId.isNullOrBlank()) {
            _selectedTargetId.value = deviceId
        }
    }

    fun selectTargetDevice(deviceId: String) {
        _selectedTargetId.value = deviceId
    }

    fun refreshLocalDirectory() {
        viewModelScope.launch {
            val localFiles = withContext(Dispatchers.IO) {
                repository.scanLocalSharedFiles()
            }
            _allItems.update { existing ->
                val existingPicked = existing.filter { it.uriString.startsWith("content://") }
                val selectedIds = existing.filter { it.isSelected }.map { it.id }.toSet()
                (existingPicked + localFiles).distinctBy { it.id }.map {
                    if (it.id in selectedIds) it.copy(isSelected = true) else it
                }
            }
        }
    }

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                repository.inspectPickedUris(uris)
            }
            _allItems.update { current ->
                (added + current).distinctBy { it.id }
            }
        }
    }

    fun addPickedFolderTree(treeUri: Uri?) {
        if (treeUri == null) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                repository.inspectPickedTreeUri(treeUri)
            } ?: return@launch
            _allItems.update { current ->
                (listOf(added) + current).distinctBy { it.id }
            }
        }
    }

    fun selectCategory(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun toggleSearchVisibility() {
        val next = !_isSearchVisible.value
        _isSearchVisible.value = next
        if (!next) _searchQuery.value = ""
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleItemSelection(itemId: String) {
        _allItems.update { list ->
            list.map { item ->
                if (item.id == itemId) item.copy(isSelected = !item.isSelected) else item
            }
        }
    }

    fun toggleSelectAllVisible() {
        val state = uiState.value
        val visibleIds = state.items.map { it.id }.toSet()
        val selectTarget = !state.allVisibleSelected
        _allItems.update { list ->
            list.map { item ->
                if (item.id in visibleIds) item.copy(isSelected = selectTarget) else item
            }
        }
    }

    fun startSending(target: NearbyDevice, onStarted: () -> Unit) {
        val selected = _allItems.value.filter { it.isSelected }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _isSending.value = true
            repository.startTransferToDevice(selected, target)
            _allItems.update { list -> list.map { it.copy(isSelected = false) } }
            _isSending.value = false
            onStarted()
        }
    }
}
