package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DeviceType

@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val deviceType: String = DeviceType.PHONE.name,
    val pairingToken: String,
    val lastHost: String,
    val lastPort: Int,
    val hotspotSsid: String = "",
    val supports5Ghz: Boolean = true,
    val isConnected: Boolean = false,
    val lastConnectedAt: Long = System.currentTimeMillis(),
)
