package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.network.DeviceFileScanner
import com.example.data.network.HotspotAndDiscoveryManager
import com.example.data.network.TransferProtocolEngine
import com.example.data.repository.WiFiShareRepository

class WiFiShareApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }
    val fileScanner: DeviceFileScanner by lazy { DeviceFileScanner(this) }
    val hotspotManager: HotspotAndDiscoveryManager by lazy { HotspotAndDiscoveryManager(this) }
    val transferEngine: TransferProtocolEngine by lazy {
        TransferProtocolEngine(
            fileScanner = fileScanner,
            transferDao = database.transferDao(),
            pairedDeviceDao = database.pairedDeviceDao(),
            onWifiPerformanceLockChanged = hotspotManager::setHighPerformanceWifiLock,
        )
    }
    val repository: WiFiShareRepository by lazy {
        WiFiShareRepository(
            pairedDeviceDao = database.pairedDeviceDao(),
            transferDao = database.transferDao(),
            settingsDataStore = settingsDataStore,
            hotspotManager = hotspotManager,
            transferEngine = transferEngine,
            fileScanner = fileScanner,
        )
    }
}
