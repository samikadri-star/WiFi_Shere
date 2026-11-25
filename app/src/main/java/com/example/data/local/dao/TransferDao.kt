package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY updatedAt DESC")
    fun observeAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE status IN ('ACTIVE', 'QUEUED', 'PAUSED') ORDER BY updatedAt DESC")
    fun observePendingTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE status = 'PAUSED' ORDER BY updatedAt DESC")
    fun observePausedTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getBySessionId(sessionId: String): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transfer: TransferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transfers: List<TransferEntity>)

    @Query("UPDATE transfers SET transferredBytes = :transferredBytes, speedBytesPerSec = :speedBytesPerSec, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        transferredBytes: Long,
        speedBytesPerSec: Long,
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE transfers SET status = :status, speedBytesPerSec = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transfers WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM transfers")
    suspend fun clearAll()
}
