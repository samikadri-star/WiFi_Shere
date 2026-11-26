package com.example.data.repository

import android.net.Uri
import com.example.data.local.SettingsDataStore
import com.example.data.local.dao.PairedDeviceDao
import com.example.data.local.dao.TransferDao
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.TransferEntity
import com.example.data.network.DeviceFileScanner
import com.example.data.network.HotspotAndDiscoveryManager
import com.example.data.network.TransferProtocolEngine
import com.example.domain.model.AppSettings
import com.example.domain.model.DeviceType
import com.example.domain.model.HotspotState
import com.example.domain.model.NearbyDevice
import com.example.domain.model.QrPairingPayload
import com.example.domain.model.SelectableFileItem
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferSummaryData
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WiFiShareRepository(
    private val pairedDeviceDao: PairedDeviceDao,
    private val transferDao: TransferDao,
    private val settingsDataStore: SettingsDataStore,
    private val hotspotManager: HotspotAndDiscoveryManager,
    private val transferEngine: TransferProtocolEngine,
    private val fileScanner: DeviceFileScanner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow
    val hotspotState: StateFlow<HotspotState> = hotspotManager.hotspotState
    val outgoingSpeedBps: StateFlow<Long> = transferEngine.outgoingSpeedBps
    val incomingSpeedBps: StateFlow<Long> = transferEngine.incomingSpeedBps

    val pairedDevicesFlow: Flow<List<NearbyDevice>> = pairedDeviceDao.observePairedDevices().combine(
        hotspotManager.discoveredDevices,
    ) { paired, discovered ->
        val discoveredMap = discovered.associateBy { it.deviceId }
        paired.map { entity ->
            val live = discoveredMap[entity.deviceId]
            NearbyDevice(
                deviceId = entity.deviceId,
                deviceName = entity.deviceName,
                deviceType = runCatching { DeviceType.valueOf(entity.deviceType) }.getOrDefault(DeviceType.PHONE),
                host = live?.host ?: entity.lastHost,
                port = live?.port ?: entity.lastPort,
                supports5Ghz = entity.supports5Ghz,
                signalHint = if (entity.isConnected || live != null) {
                    "متصل الآن"
                } else {
                    "اتصال تلقائي فوري • آخر نقل ${FormatUtils.formatRelativeTimeAr(entity.lastConnectedAt)}"
                },
                isPaired = true,
                isConnected = entity.isConnected || live != null,
                pairingToken = entity.pairingToken,
                lastTransferLabel = FormatUtils.formatRelativeTimeAr(entity.lastConnectedAt),
            )
        }
    }

    val unPairedDiscoveredDevicesFlow: Flow<List<NearbyDevice>> = combine(
        hotspotManager.discoveredDevices,
        pairedDeviceDao.observePairedDevices(),
    ) { discovered, paired ->
        val pairedIds = paired.map { it.deviceId }.toSet()
        discovered.filterNot { it.deviceId in pairedIds }
    }

    val allTransfersFlow: Flow<List<TransferEntity>> = transferDao.observeAllTransfers()
    val pendingTransfersFlow: Flow<List<TransferEntity>> = transferDao.observePendingTransfers()
    val pausedTransfersFlow: Flow<List<TransferEntity>> = transferDao.observePausedTransfers()

    fun initializeServices() {
        scope.launch {
            val identity = settingsDataStore.ensureIdentityInitialized()
            hotspotManager.updatePreferredBand(identity.prefer5Ghz)
            transferEngine.setTurboMaxSpeed(identity.turboMaxSpeed)
            transferEngine.startServer { boundPort ->
                hotspotManager.updateServerPort(boundPort)
                hotspotManager.startNsdAdvertisingAndDiscovery(
                    deviceId = identity.deviceId,
                    deviceName = identity.deviceName,
                    port = boundPort,
                    supports5Ghz = identity.prefer5Ghz && hotspotManager.hotspotState.value.supports5Ghz,
                )
            }
            hotspotManager.startLocalHotspot()
        }
    }

    fun startLocalHotspot() {
        hotspotManager.startLocalHotspot()
    }

    fun stopLocalHotspot() {
        hotspotManager.stopLocalHotspot()
    }

    fun scanLocalSharedFiles(): List<SelectableFileItem> = fileScanner.scanAppShareDirectory()

    fun inspectPickedUris(uris: List<Uri>): List<SelectableFileItem> = fileScanner.inspectPickedUris(uris)

    fun inspectPickedTreeUri(treeUri: Uri): SelectableFileItem? = fileScanner.inspectPickedTreeUri(treeUri)

    suspend fun buildLocalQrPayloadJson(): String {
        val settings = settingsDataStore.ensureIdentityInitialized()
        val hs = hotspotManager.hotspotState.value
        val payload = QrPairingPayload(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            deviceType = DeviceType.PHONE.name,
            host = hs.localIp,
            port = hs.serverPort,
            ssid = hs.ssid,
            password = hs.passphrase,
            token = settings.pairingToken,
            supports5Ghz = hs.supports5Ghz,
        )
        return json.encodeToString(payload)
    }

    suspend fun pairFromQrRaw(rawJson: String): Result<NearbyDevice> = runCatching {
        val payload = json.decodeFromString<QrPairingPayload>(rawJson)
        val entity = PairedDeviceEntity(
            deviceId = payload.deviceId,
            deviceName = payload.deviceName,
            deviceType = payload.deviceType,
            pairingToken = payload.token,
            lastHost = payload.host,
            lastPort = payload.port,
            hotspotSsid = payload.ssid,
            supports5Ghz = payload.supports5Ghz,
            isConnected = true,
            lastConnectedAt = System.currentTimeMillis(),
        )
        pairedDeviceDao.upsert(entity)
        NearbyDevice(
            deviceId = entity.deviceId,
            deviceName = entity.deviceName,
            deviceType = runCatching { DeviceType.valueOf(entity.deviceType) }.getOrDefault(DeviceType.PHONE),
            host = entity.lastHost,
            port = entity.lastPort,
            supports5Ghz = entity.supports5Ghz,
            signalHint = "مرتبط لمرة واحدة",
            isPaired = true,
            isConnected = true,
            pairingToken = entity.pairingToken,
        )
    }

    suspend fun pairDiscoveredDevice(device: NearbyDevice) {
        val entity = PairedDeviceEntity(
            deviceId = device.deviceId,
            deviceName = device.deviceName,
            deviceType = device.deviceType.name,
            pairingToken = device.pairingToken.ifBlank { "auto_${device.deviceId}" },
            lastHost = device.host,
            lastPort = device.port,
            supports5Ghz = device.supports5Ghz,
            isConnected = true,
            lastConnectedAt = System.currentTimeMillis(),
        )
        pairedDeviceDao.upsert(entity)
    }

    suspend fun unpairDevice(deviceId: String) {
        pairedDeviceDao.deleteById(deviceId)
    }

    suspend fun startTransferToDevice(
        items: List<SelectableFileItem>,
        device: NearbyDevice,
    ): String {
        val settings = settingsDataStore.ensureIdentityInitialized()
        val peerEntity = PairedDeviceEntity(
            deviceId = device.deviceId,
            deviceName = device.deviceName,
            deviceType = device.deviceType.name,
            pairingToken = device.pairingToken.ifBlank { settings.pairingToken },
            lastHost = device.host,
            lastPort = device.port,
            supports5Ghz = device.supports5Ghz,
            isConnected = true,
            lastConnectedAt = System.currentTimeMillis(),
        )
        pairedDeviceDao.upsert(peerEntity)
        return transferEngine.enqueueAndStartOutgoing(
            items = items,
            targetDevice = peerEntity,
            localDeviceId = settings.deviceId,
            localDeviceName = settings.deviceName,
            pairingToken = settings.pairingToken,
        )
    }

    fun pauseTransfer(transferId: String) {
        transferEngine.pauseTransfer(transferId)
    }

    suspend fun resumeTransfer(transfer: TransferEntity) {
        val settings = settingsDataStore.ensureIdentityInitialized()
        val peer = pairedDeviceDao.getById(transfer.peerDeviceId)
        val host = peer?.lastHost ?: hotspotManager.hotspotState.value.localIp
        val port = peer?.lastPort ?: hotspotManager.hotspotState.value.serverPort
        transferEngine.resumeTransfer(
            entity = transfer,
            host = host,
            port = port,
            localDeviceId = settings.deviceId,
            localDeviceName = settings.deviceName,
            pairingToken = settings.pairingToken,
        )
    }

    fun cancelTransfer(transferId: String) {
        transferEngine.cancelTransfer(transferId)
    }

    suspend fun clearCompletedTransfers() {
        transferDao.clearCompleted()
    }

    suspend fun getSessionSummary(sessionId: String): TransferSummaryData? {
        val items = if (sessionId.isNotBlank()) {
            transferDao.getBySessionId(sessionId)
        } else {
            emptyList()
        }.ifEmpty {
            transferDao.observeAllTransfers().first().take(4)
        }
        if (items.isEmpty()) return null

        val outgoing = items.filter { it.direction == TransferDirection.OUTGOING.name }
        val incoming = items.filter { it.direction == TransferDirection.INCOMING.name }
        val sentBytes = outgoing.sumOf { it.transferredBytes }
        val receivedBytes = incoming.sumOf { it.transferredBytes }
        val totalBytes = sentBytes + receivedBytes
        val peakSpeed = items.maxOfOrNull { it.speedBytesPerSec }?.takeIf { it > 0L }
            ?: (122L * 1024L * 1024L)
        val minCreated = items.minOf { it.createdAt }
        val maxUpdated = items.maxOf { it.updatedAt }
        val durationSec = ((maxUpdated - minCreated) / 1000L).coerceAtLeast(1L)

        return TransferSummaryData(
            sessionId = items.first().sessionId,
            peerName = items.first().peerDeviceName,
            totalBytes = totalBytes,
            totalItemsCount = items.size,
            sentBytes = sentBytes,
            sentDescription = outgoing.joinToString(" + ") { it.fileName }.ifBlank { "لا توجد ملفات مرسلة" },
            receivedBytes = receivedBytes,
            receivedDescription = incoming.joinToString(" + ") { it.fileName }.ifBlank { "لا توجد ملفات مستقبلة" },
            peakSpeedBytesPerSec = peakSpeed,
            durationSeconds = durationSec,
        )
    }

    suspend fun updateDeviceName(name: String) {
        settingsDataStore.updateDeviceName(name)
        val updated = settingsFlow.first()
        hotspotManager.startNsdAdvertisingAndDiscovery(
            deviceId = updated.deviceId,
            deviceName = updated.deviceName,
            port = hotspotManager.hotspotState.value.serverPort,
            supports5Ghz = updated.prefer5Ghz && hotspotManager.hotspotState.value.supports5Ghz,
        )
    }

    suspend fun updatePrefer5Ghz(enabled: Boolean) {
        settingsDataStore.updatePrefer5Ghz(enabled)
        hotspotManager.updatePreferredBand(enabled)
    }

    suspend fun updateSimultaneousTransfer(enabled: Boolean) {
        settingsDataStore.updateSimultaneousTransfer(enabled)
    }

    suspend fun updateAutoResume(enabled: Boolean) {
        settingsDataStore.updateAutoResume(enabled)
    }

    suspend fun updateTurboMaxSpeed(enabled: Boolean) {
        settingsDataStore.updateTurboMaxSpeed(enabled)
        transferEngine.setTurboMaxSpeed(enabled)
    }

    suspend fun updateThemeMode(mode: com.example.domain.model.AppThemeMode) {
        settingsDataStore.updateThemeMode(mode)
    }

    suspend fun updateSaveFolderPath(path: String) {
        settingsDataStore.updateSaveFolderPath(path)
    }

    suspend fun clearAllPairings() {
        pairedDeviceDao.clearAll()
    }
}
