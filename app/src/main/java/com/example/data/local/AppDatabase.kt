package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.PairedDeviceDao
import com.example.data.local.dao.TransferDao
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.TransferEntity

@Database(
    entities = [PairedDeviceEntity::class, TransferEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pairedDeviceDao(): PairedDeviceDao
    abstract fun transferDao(): TransferDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifishare.db",
                ).build().also { instance = it }
            }
        }
    }
}
