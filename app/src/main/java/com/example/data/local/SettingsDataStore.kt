package com.example.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wifishare_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val PAIRING_TOKEN = stringPreferencesKey("pairing_token")
        val PREFER_5GHZ = booleanPreferencesKey("prefer_5ghz")
        val SIMULTANEOUS_TRANSFER = booleanPreferencesKey("simultaneous_transfer")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val SAVE_FOLDER_PATH = stringPreferencesKey("save_folder_path")
    }

    private val defaultDeviceName: String
        get() = "جهازي — ${Build.MODEL ?: "Android"}"

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            deviceId = prefs[Keys.DEVICE_ID].orEmpty(),
            deviceName = prefs[Keys.DEVICE_NAME] ?: defaultDeviceName,
            pairingToken = prefs[Keys.PAIRING_TOKEN].orEmpty(),
            prefer5Ghz = prefs[Keys.PREFER_5GHZ] ?: true,
            simultaneousTransfer = prefs[Keys.SIMULTANEOUS_TRANSFER] ?: true,
            autoResume = prefs[Keys.AUTO_RESUME] ?: true,
            saveFolderPath = prefs[Keys.SAVE_FOLDER_PATH] ?: "/Download/WiFiShare",
        )
    }

    suspend fun ensureIdentityInitialized(): AppSettings {
        val current = context.settingsDataStore.data.first()
        if (current[Keys.DEVICE_ID].isNullOrBlank() || current[Keys.PAIRING_TOKEN].isNullOrBlank()) {
            context.settingsDataStore.edit { prefs ->
                if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                    prefs[Keys.DEVICE_ID] = UUID.randomUUID().toString().take(12)
                }
                if (prefs[Keys.PAIRING_TOKEN].isNullOrBlank()) {
                    prefs[Keys.PAIRING_TOKEN] = UUID.randomUUID().toString().replace("-", "").take(16)
                }
                if (prefs[Keys.DEVICE_NAME].isNullOrBlank()) {
                    prefs[Keys.DEVICE_NAME] = defaultDeviceName
                }
            }
        }
        return settingsFlow.first()
    }

    suspend fun updateDeviceName(name: String) {
        val trimmed = name.trim().ifEmpty { defaultDeviceName }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEVICE_NAME] = trimmed
        }
    }

    suspend fun updatePrefer5Ghz(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PREFER_5GHZ] = enabled
        }
    }

    suspend fun updateSimultaneousTransfer(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SIMULTANEOUS_TRANSFER] = enabled
        }
    }

    suspend fun updateAutoResume(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_RESUME] = enabled
        }
    }

    suspend fun updateSaveFolderPath(path: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SAVE_FOLDER_PATH] = path.ifBlank { "/Download/WiFiShare" }
        }
    }
}
