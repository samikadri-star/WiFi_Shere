package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WiFiShareApp
import com.example.data.local.entity.TransferEntity
import com.example.data.network.QrCodeUtils
import com.example.domain.model.AppSettings
import com.example.domain.model.HotspotState
import com.example.domain.model.NearbyDevice
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferStatus
import com.example.domain.model.TransferSummaryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainShellUiState(
    val hotspotState: HotspotState = HotspotState(),
    val settings: AppSettings = AppSettings(),
    val activeTransferCount: Int = 0,
    val combinedSpeedBps: Long = 0L,
    val outgoingSpeedBps: Long = 0L,
    val incomingSpeedBps: Long = 0L,
    val isReceiveReady: Boolean = true,
)

data class TransfersUiState(
    val activePeer: NearbyDevice? = null,
    val outgoingTransfers: List<TransferEntity> = emptyList(),
    val incomingTransfers: List<TransferEntity> = emptyList(),
    val outgoingSpeedBps: Long = 0L,
    val incomingSpeedBps: Long = 0L,
    val estimatedRemainingSeconds: Long = 0L,
    val hasCompletedSession: Boolean = false,
    val latestCompletedSessionId: String = "",
)

data class DevicesUiState(
    val pairedDevices: List<NearbyDevice> = emptyList(),
    val discoveredDevices: List<NearbyDevice> = emptyList(),
    val isScanning: Boolean = true,
)

data class QrPairingUiState(
    val myQrBitmap: Bitmap? = null,
    val myQrRawPayload: String = "",
    val isFlashEnabled: Boolean = false,
    val pairedSuccessDevice: NearbyDevice? = null,
    val scanErrorMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as WiFiShareApp).repository

    private val _receiveReady = MutableStateFlow(true)
    private val _qrUiState = MutableStateFlow(QrPairingUiState())
    val qrUiState: StateFlow<QrPairingUiState> = _qrUiState.asStateFlow()

    private val _summaryState = MutableStateFlow<TransferSummaryData?>(null)
    val summaryState: StateFlow<TransferSummaryData?> = _summaryState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    val shellState: StateFlow<MainShellUiState> = combine(
        repository.hotspotState,
        repository.settingsFlow,
        repository.pendingTransfersFlow,
        repository.outgoingSpeedBps,
        repository.incomingSpeedBps,
        _receiveReady,
    ) { values ->
        val hotspot = values[0] as HotspotState
        val settings = values[1] as AppSettings
        @Suppress("UNCHECKED_CAST")
        val pending = values[2] as List<TransferEntity>
        val outBps = values[3] as Long
        val inBps = values[4] as Long
        val receiveReady = values[5] as Boolean
        MainShellUiState(
            hotspotState = hotspot,
            settings = settings,
            activeTransferCount = pending.size,
            combinedSpeedBps = outBps + inBps,
            outgoingSpeedBps = outBps,
            incomingSpeedBps = inBps,
            isReceiveReady = receiveReady,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainShellUiState(),
    )

    val pausedTransfers: StateFlow<List<TransferEntity>> = repository.pausedTransfersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val devicesUiState: StateFlow<DevicesUiState> = combine(
        repository.pairedDevicesFlow,
        repository.unPairedDiscoveredDevicesFlow,
    ) { paired, discovered ->
        DevicesUiState(
            pairedDevices = paired,
            discoveredDevices = discovered,
            isScanning = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevicesUiState(),
    )

    val transfersUiState: StateFlow<TransfersUiState> = combine(
        repository.allTransfersFlow,
        repository.pairedDevicesFlow,
        repository.outgoingSpeedBps,
        repository.incomingSpeedBps,
    ) { all, paired, outBps, inBps ->
        val outgoing = all.filter { it.direction == TransferDirection.OUTGOING.name }
        val incoming = all.filter { it.direction == TransferDirection.INCOMING.name }
        val remainingBytes = all
            .filter { it.status == TransferStatus.ACTIVE.name || it.status == TransferStatus.QUEUED.name }
            .sumOf { (it.totalBytes - it.transferredBytes).coerceAtLeast(0L) }
        val totalSpeed = (outBps + inBps).coerceAtLeast(1L)
        val etaSec = if (remainingBytes > 0L && (outBps + inBps) > 0L) remainingBytes / totalSpeed else 0L
        val completed = all.filter { it.status == TransferStatus.COMPLETED.name }
        val activePeerId = all.firstOrNull()?.peerDeviceId
        val activePeer = paired.firstOrNull { it.deviceId == activePeerId } ?: paired.firstOrNull()

        TransfersUiState(
            activePeer = activePeer,
            outgoingTransfers = outgoing,
            incomingTransfers = incoming,
            outgoingSpeedBps = outBps,
            incomingSpeedBps = inBps,
            estimatedRemainingSeconds = etaSec,
            hasCompletedSession = completed.isNotEmpty(),
            latestCompletedSessionId = completed.firstOrNull()?.sessionId.orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransfersUiState(),
    )

    init {
        repository.initializeServices()
    }

    fun startHotspotAfterPermission() {
        repository.startLocalHotspot()
    }

    fun toggleReceiveReady() {
        _receiveReady.value = !_receiveReady.value
        if (_receiveReady.value) {
            repository.startLocalHotspot()
            _statusBannerMessage.value = "جاهز لاستقبال الملفات عبر نقطة الاتصال"
        } else {
            _statusBannerMessage.value = "تم إيقاف وضع الاستقبال المؤقت"
        }
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun pauseTransfer(transferId: String) {
        repository.pauseTransfer(transferId)
    }

    fun resumeTransfer(transfer: TransferEntity) {
        viewModelScope.launch {
            repository.resumeTransfer(transfer)
        }
    }

    fun cancelTransfer(transferId: String) {
        repository.cancelTransfer(transferId)
    }

    fun clearCompletedTransfers() {
        viewModelScope.launch {
            repository.clearCompletedTransfers()
        }
    }

    fun pairDiscoveredDevice(device: NearbyDevice) {
        viewModelScope.launch {
            repository.pairDiscoveredDevice(device)
            _statusBannerMessage.value = "تم ربط ${device.deviceName} لمرة واحدة"
        }
    }

    fun unpairDevice(deviceId: String) {
        viewModelScope.launch {
            repository.unpairDevice(deviceId)
        }
    }

    fun refreshLocalQrCode() {
        viewModelScope.launch {
            val payload = repository.buildLocalQrPayloadJson()
            val bitmap = withContext(Dispatchers.Default) {
                QrCodeUtils.generateQrBitmap(payload, 540)
            }
            _qrUiState.value = _qrUiState.value.copy(
                myQrBitmap = bitmap,
                myQrRawPayload = payload,
            )
        }
    }

    fun onQrScanned(rawContent: String) {
        if (_qrUiState.value.pairedSuccessDevice != null) return
        viewModelScope.launch {
            repository.pairFromQrRaw(rawContent)
                .onSuccess { device ->
                    _qrUiState.value = _qrUiState.value.copy(
                        pairedSuccessDevice = device,
                        scanErrorMessage = null,
                    )
                }
                .onFailure {
                    _qrUiState.value = _qrUiState.value.copy(
                        scanErrorMessage = "الرمز غير صالح، تأكد من مسح رمز واي فاي شير",
                    )
                }
        }
    }

    fun toggleQrFlash() {
        _qrUiState.value = _qrUiState.value.copy(
            isFlashEnabled = !_qrUiState.value.isFlashEnabled,
        )
    }

    fun resetQrScanState() {
        _qrUiState.value = _qrUiState.value.copy(
            pairedSuccessDevice = null,
            scanErrorMessage = null,
        )
    }

    fun loadSummary(sessionId: String) {
        viewModelScope.launch {
            _summaryState.value = repository.getSessionSummary(sessionId)
        }
    }

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            repository.updateDeviceName(name)
        }
    }

    fun updatePrefer5Ghz(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePrefer5Ghz(enabled)
        }
    }

    fun updateSimultaneousTransfer(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSimultaneousTransfer(enabled)
        }
    }

    fun updateAutoResume(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoResume(enabled)
        }
    }

    fun updateSaveFolderPath(path: String) {
        viewModelScope.launch {
            repository.updateSaveFolderPath(path)
        }
    }

    fun clearAllPairings() {
        viewModelScope.launch {
            repository.clearAllPairings()
            _statusBannerMessage.value = "تم مسح سجل الارتباطات المحفوظة"
        }
    }
}
