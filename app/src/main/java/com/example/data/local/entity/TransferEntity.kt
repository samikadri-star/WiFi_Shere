package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferStatus

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val peerDeviceId: String,
    val peerDeviceName: String,
    val fileName: String,
    val mimeType: String,
    val category: String,
    val direction: String = TransferDirection.OUTGOING.name,
    val isFolder: Boolean = false,
    val itemCount: Int = 1,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    val status: String = TransferStatus.QUEUED.name,
    val sourceUri: String = "",
    val destinationPath: String = "",
    val speedBytesPerSec: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0L) 0f else (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}
