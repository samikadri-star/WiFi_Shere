package com.example.domain.model

import kotlinx.serialization.Serializable

enum class DeviceType {
    PHONE,
    TABLET,
    LAPTOP,
}

enum class FileCategory(val labelAr: String) {
    ALL("الكل"),
    FOLDER("المجلدات"),
    IMAGE("الصور"),
    VIDEO("الفيديو"),
    APP("التطبيقات"),
    DOCUMENT("المستندات"),
}

enum class TransferDirection {
    OUTGOING,
    INCOMING,
}

enum class TransferStatus {
    QUEUED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED,
}

data class NearbyDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType = DeviceType.PHONE,
    val host: String,
    val port: Int,
    val supports5Ghz: Boolean = true,
    val signalHint: String = "مكتشف تلقائياً • قريب جداً",
    val isPaired: Boolean = false,
    val isConnected: Boolean = false,
    val pairingToken: String = "",
    val lastTransferLabel: String = "",
)

data class SelectableFileItem(
    val id: String,
    val name: String,
    val uriString: String,
    val sizeBytes: Long,
    val category: FileCategory,
    val isFolder: Boolean = false,
    val fileCount: Int = 1,
    val mimeType: String = "*/*",
    val extensionLabel: String = "FILE",
    val isSelected: Boolean = false,
)

data class HotspotState(
    val isActive: Boolean = true,
    val isLocalHotspotOwned: Boolean = false,
    val ssid: String = "WiFiShare_5G",
    val passphrase: String = "",
    val supports5Ghz: Boolean = true,
    val localIp: String = "192.168.43.1",
    val serverPort: Int = 54321,
    val statusText: String = "نقطة الاتصال نشطة",
    val bandLabel: String = "5 GHz",
    val errorMessage: String? = null,
)

@Serializable
data class QrPairingPayload(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = DeviceType.PHONE.name,
    val host: String,
    val port: Int,
    val ssid: String = "",
    val password: String = "",
    val token: String,
    val supports5Ghz: Boolean = true,
)

data class AppSettings(
    val deviceId: String = "",
    val deviceName: String = "جهازي",
    val pairingToken: String = "",
    val prefer5Ghz: Boolean = true,
    val simultaneousTransfer: Boolean = true,
    val autoResume: Boolean = true,
    val saveFolderPath: String = "/Download/WiFiShare",
)

data class TransferSummaryData(
    val sessionId: String,
    val peerName: String,
    val totalBytes: Long,
    val totalItemsCount: Int,
    val sentBytes: Long,
    val sentDescription: String,
    val receivedBytes: Long,
    val receivedDescription: String,
    val peakSpeedBytesPerSec: Long,
    val durationSeconds: Long,
)
