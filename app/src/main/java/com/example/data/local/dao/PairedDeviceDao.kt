package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.PairedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices ORDER BY isConnected DESC, lastConnectedAt DESC")
    fun observePairedDevices(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getById(deviceId: String): PairedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: PairedDeviceEntity)

    @Query("UPDATE paired_devices SET isConnected = :isConnected, lastHost = :host, lastPort = :port, lastConnectedAt = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateConnection(deviceId: String, isConnected: Boolean, host: String, port: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE paired_devices SET isConnected = 0")
    suspend fun markAllDisconnected()

    @Query("DELETE FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query("DELETE FROM paired_devices")
    suspend fun clearAll()
}
