package com.example.data.network

import com.example.data.local.dao.PairedDeviceDao
import com.example.data.local.dao.TransferDao
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.TransferEntity
import com.example.domain.model.SelectableFileItem
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class TransferHeaderFrame(
    val transferId: String,
    val sessionId: String,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val pairingToken: String,
    val fileName: String,
    val mimeType: String,
    val category: String,
    val isFolder: Boolean,
    val itemCount: Int,
    val totalBytes: Long,
    val requestedOffset: Long,
)

@Serializable
private data class TransferAckFrame(
    val accepted: Boolean,
    val resumeOffset: Long = 0L,
    val message: String = "",
)

class TransferProtocolEngine(
    private val fileScanner: DeviceFileScanner,
    private val transferDao: TransferDao,
    private val pairedDeviceDao: PairedDeviceDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _listeningPort = MutableStateFlow(54321)
    val listeningPort: StateFlow<Int> = _listeningPort.asStateFlow()

    private val _outgoingSpeedBps = MutableStateFlow(0L)
    val outgoingSpeedBps: StateFlow<Long> = _outgoingSpeedBps.asStateFlow()

    private val _incomingSpeedBps = MutableStateFlow(0L)
    val incomingSpeedBps: StateFlow<Long> = _incomingSpeedBps.asStateFlow()

    private val activeOutgoingSpeeds = ConcurrentHashMap<String, Long>()
    private val activeIncomingSpeeds = ConcurrentHashMap<String, Long>()

    fun startServer(preferredPort: Int = 54321, onPortBound: (Int) -> Unit) {
        if (acceptJob?.isActive == true) {
            onPortBound(_listeningPort.value)
            return
        }
        acceptJob = scope.launch {
            val socket = openServerSocket(preferredPort) ?: return@launch
            serverSocket = socket
            _listeningPort.value = socket.localPort
            onPortBound(socket.localPort)
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { handleIncomingConnection(client) }
            }
        }
    }

    fun stopServer() {
        acceptJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    suspend fun enqueueAndStartOutgoing(
        items: List<SelectableFileItem>,
        targetDevice: PairedDeviceEntity,
        localDeviceId: String,
        localDeviceName: String,
        pairingToken: String,
    ): String {
        val sessionId = UUID.randomUUID().toString()
        val entities = items.map { item ->
            TransferEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                peerDeviceId = targetDevice.deviceId,
                peerDeviceName = targetDevice.deviceName,
                fileName = item.name,
                mimeType = item.mimeType,
                category = item.category.name,
                direction = TransferDirection.OUTGOING.name,
                isFolder = item.isFolder,
                itemCount = item.fileCount,
                totalBytes = item.sizeBytes.coerceAtLeast(1L),
                transferredBytes = 0L,
                status = TransferStatus.QUEUED.name,
                sourceUri = item.uriString,
                destinationPath = "",
            )
        }
        transferDao.upsertAll(entities)

        entities.forEach { entity ->
            launchOutgoingTransfer(
                entity = entity,
                host = targetDevice.lastHost,
                port = targetDevice.lastPort,
                localDeviceId = localDeviceId,
                localDeviceName = localDeviceName,
                pairingToken = pairingToken,
            )
        }
        return sessionId
    }

    fun pauseTransfer(transferId: String) {
        activeJobs[transferId]?.cancel()
        activeJobs.remove(transferId)
        activeOutgoingSpeeds.remove(transferId)
        activeIncomingSpeeds.remove(transferId)
        recomputeAggregateSpeeds()
        scope.launch {
            transferDao.updateStatus(transferId, TransferStatus.PAUSED.name)
        }
    }

    fun resumeTransfer(
        entity: TransferEntity,
        host: String,
        port: Int,
        localDeviceId: String,
        localDeviceName: String,
        pairingToken: String,
    ) {
        if (entity.direction == TransferDirection.OUTGOING.name) {
            launchOutgoingTransfer(
                entity = entity,
                host = host,
                port = port,
                localDeviceId = localDeviceId,
                localDeviceName = localDeviceName,
                pairingToken = pairingToken,
            )
        } else {
            scope.launch {
                transferDao.updateStatus(entity.id, TransferStatus.QUEUED.name)
            }
        }
    }

    fun cancelTransfer(transferId: String) {
        activeJobs[transferId]?.cancel()
        activeJobs.remove(transferId)
        activeOutgoingSpeeds.remove(transferId)
        activeIncomingSpeeds.remove(transferId)
        recomputeAggregateSpeeds()
        scope.launch {
            transferDao.deleteById(transferId)
        }
    }

    private fun launchOutgoingTransfer(
        entity: TransferEntity,
        host: String,
        port: Int,
        localDeviceId: String,
        localDeviceName: String,
        pairingToken: String,
    ) {
        activeJobs[entity.id]?.cancel()
        val job = scope.launch {
            var socket: Socket? = null
            try {
                transferDao.updateStatus(entity.id, TransferStatus.ACTIVE.name)
                socket = Socket().apply {
                    soTimeout = 15_000
                    connect(InetSocketAddress(host, port), 6_000)
                }

                val outStream = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val header = TransferHeaderFrame(
                    transferId = entity.id,
                    sessionId = entity.sessionId,
                    senderDeviceId = localDeviceId,
                    senderDeviceName = localDeviceName,
                    pairingToken = pairingToken,
                    fileName = entity.fileName,
                    mimeType = entity.mimeType,
                    category = entity.category,
                    isFolder = entity.isFolder,
                    itemCount = entity.itemCount,
                    totalBytes = entity.totalBytes,
                    requestedOffset = entity.transferredBytes,
                )
                outStream.write((json.encodeToString(header) + "\n").toByteArray(Charsets.UTF_8))
                outStream.flush()

                val ackLine = reader.readLine() ?: throw IllegalStateException("No ack from peer")
                val ack = json.decodeFromString<TransferAckFrame>(ackLine)
                if (!ack.accepted) {
                    transferDao.updateStatus(entity.id, TransferStatus.FAILED.name)
                    return@launch
                }

                val startOffset = ack.resumeOffset.coerceIn(0L, entity.totalBytes)
                val inputStream = fileScanner.openStreamForItem(entity.sourceUri)
                    ?: throw IllegalStateException("Cannot open source URI")

                inputStream.use { rawIn ->
                    val bufferedIn = BufferedInputStream(rawIn, 64 * 1024)
                    if (startOffset > 0L) {
                        var toSkip = startOffset
                        while (toSkip > 0L) {
                            val skipped = bufferedIn.skip(toSkip)
                            if (skipped <= 0L) break
                            toSkip -= skipped
                        }
                    }

                    val buffer = ByteArray(64 * 1024)
                    var sentTotal = startOffset
                    var windowBytes = 0L
                    var windowStart = System.currentTimeMillis()

                    while (isActive) {
                        val read = bufferedIn.read(buffer)
                        if (read == -1) break
                        outStream.write(buffer, 0, read)
                        sentTotal += read
                        windowBytes += read

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - windowStart
                        if (elapsedMs >= 160L) {
                            val bps = (windowBytes * 1000L) / elapsedMs.coerceAtLeast(1L)
                            activeOutgoingSpeeds[entity.id] = bps
                            recomputeAggregateSpeeds()
                            transferDao.updateProgress(
                                id = entity.id,
                                transferredBytes = sentTotal.coerceAtMost(entity.totalBytes),
                                speedBytesPerSec = bps,
                                status = TransferStatus.ACTIVE.name,
                            )
                            windowBytes = 0L
                            windowStart = now
                        }
                    }
                    outStream.flush()
                    activeOutgoingSpeeds.remove(entity.id)
                    recomputeAggregateSpeeds()
                    transferDao.updateProgress(
                        id = entity.id,
                        transferredBytes = entity.totalBytes,
                        speedBytesPerSec = 0L,
                        status = TransferStatus.COMPLETED.name,
                    )
                }
            } catch (_: CancellationException) {
                activeOutgoingSpeeds.remove(entity.id)
                recomputeAggregateSpeeds()
                transferDao.updateStatus(entity.id, TransferStatus.PAUSED.name)
            } catch (_: Exception) {
                activeOutgoingSpeeds.remove(entity.id)
                recomputeAggregateSpeeds()
                // Keep exact progress paused so user can resume when peer reconnects
                transferDao.updateStatus(entity.id, TransferStatus.PAUSED.name)
            } finally {
                activeJobs.remove(entity.id)
                runCatching { socket?.close() }
            }
        }
        activeJobs[entity.id] = job
    }

    private suspend fun handleIncomingConnection(socket: Socket) {
        var currentTransferId: String? = null
        try {
            socket.soTimeout = 20_000
            val rawIn = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            val lineBuffer = StringBuilder()
            while (true) {
                val b = rawIn.read()
                if (b == -1 || b == '\n'.code) break
                lineBuffer.append(b.toChar())
                if (lineBuffer.length > 8192) break
            }
            if (lineBuffer.isEmpty()) return

            val header = json.decodeFromString<TransferHeaderFrame>(lineBuffer.toString())
            currentTransferId = header.transferId

            val existingPeer = pairedDeviceDao.getById(header.senderDeviceId)
            val peerIp = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress.orEmpty()
            if (existingPeer == null) {
                pairedDeviceDao.upsert(
                    PairedDeviceEntity(
                        deviceId = header.senderDeviceId,
                        deviceName = header.senderDeviceName,
                        pairingToken = header.pairingToken,
                        lastHost = peerIp,
                        lastPort = 54321,
                        isConnected = true,
                    ),
                )
            } else {
                pairedDeviceDao.updateConnection(
                    deviceId = existingPeer.deviceId,
                    isConnected = true,
                    host = peerIp.ifBlank { existingPeer.lastHost },
                    port = existingPeer.lastPort,
                )
            }

            val partFile = File(fileScanner.getStagingDirectory(), "${header.transferId}.part")
            val existingLength = if (partFile.exists()) partFile.length() else 0L
            val resumeOffset = existingLength.coerceAtMost(header.totalBytes)

            val entity = TransferEntity(
                id = header.transferId,
                sessionId = header.sessionId,
                peerDeviceId = header.senderDeviceId,
                peerDeviceName = header.senderDeviceName,
                fileName = header.fileName,
                mimeType = header.mimeType,
                category = header.category,
                direction = TransferDirection.INCOMING.name,
                isFolder = header.isFolder,
                itemCount = header.itemCount,
                totalBytes = header.totalBytes.coerceAtLeast(1L),
                transferredBytes = resumeOffset,
                status = TransferStatus.ACTIVE.name,
            )
            transferDao.upsert(entity)

            val ackOut = socket.getOutputStream()
            val ackJson = json.encodeToString(TransferAckFrame(accepted = true, resumeOffset = resumeOffset)) + "\n"
            ackOut.write(ackJson.toByteArray(Charsets.UTF_8))
            ackOut.flush()

            var receivedTotal = resumeOffset
            var windowBytes = 0L
            var windowStart = System.currentTimeMillis()
            val buffer = ByteArray(64 * 1024)

            RandomAccessFile(partFile, "rw").use { raf ->
                raf.seek(resumeOffset)
                while (receivedTotal < header.totalBytes) {
                    val read = rawIn.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    receivedTotal += read
                    windowBytes += read

                    val now = System.currentTimeMillis()
                    val elapsedMs = now - windowStart
                    if (elapsedMs >= 160L) {
                        val bps = (windowBytes * 1000L) / elapsedMs.coerceAtLeast(1L)
                        activeIncomingSpeeds[header.transferId] = bps
                        recomputeAggregateSpeeds()
                        transferDao.updateProgress(
                            id = header.transferId,
                            transferredBytes = receivedTotal.coerceAtMost(header.totalBytes),
                            speedBytesPerSec = bps,
                            status = TransferStatus.ACTIVE.name,
                        )
                        windowBytes = 0L
                        windowStart = now
                    }
                }
            }

            activeIncomingSpeeds.remove(header.transferId)
            recomputeAggregateSpeeds()

            if (receivedTotal >= header.totalBytes) {
                val savedUri = fileScanner.saveCompletedFile(header.fileName, header.mimeType, partFile)
                transferDao.upsert(
                    entity.copy(
                        transferredBytes = header.totalBytes,
                        speedBytesPerSec = 0L,
                        status = TransferStatus.COMPLETED.name,
                        destinationPath = savedUri,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                transferDao.updateProgress(
                    id = header.transferId,
                    transferredBytes = receivedTotal,
                    speedBytesPerSec = 0L,
                    status = TransferStatus.PAUSED.name,
                )
            }
        } catch (_: Exception) {
            currentTransferId?.let { id ->
                activeIncomingSpeeds.remove(id)
                recomputeAggregateSpeeds()
                transferDao.updateStatus(id, TransferStatus.PAUSED.name)
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun openServerSocket(preferredPort: Int): ServerSocket? {
        return runCatching {
            ServerSocket(preferredPort)
        }.recoverCatching {
            ServerSocket(0)
        }.getOrNull()
    }

    private fun recomputeAggregateSpeeds() {
        _outgoingSpeedBps.value = activeOutgoingSpeeds.values.sum()
        _incomingSpeedBps.value = activeIncomingSpeeds.values.sum()
    }
}
