package com.tailsync.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tailsync_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SERVER_PORT = intPreferencesKey("server_port")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val LAST_SYNCED_TEXT = stringPreferencesKey("last_synced_text")
        private val LAST_SYNCED_TIME = longPreferencesKey("last_synced_time")

        const val DEFAULT_PORT = 8765
        const val DEFAULT_URL = ""
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SERVER_URL] ?: DEFAULT_URL
    }

    val serverPort: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SERVER_PORT] ?: DEFAULT_PORT
    }

    val autoConnect: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CONNECT] ?: true
    }

    val lastSyncedText: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_SYNCED_TEXT] ?: ""
    }

    val lastSyncedTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SYNCED_TIME] ?: 0L
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }

    suspend fun setServerPort(port: Int) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_PORT] = port
        }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CONNECT] = enabled
        }
    }

    suspend fun setLastSynced(text: String, time: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNCED_TEXT] = text
            preferences[LAST_SYNCED_TIME] = time
        }
    }

    suspend fun getServerUrlSync(): String {
        var url = DEFAULT_URL
        context.dataStore.edit { preferences ->
            url = preferences[SERVER_URL] ?: DEFAULT_URL
        }
        return url
    }

    suspend fun getServerPortSync(): Int {
        var port = DEFAULT_PORT
        context.dataStore.edit { preferences ->
            port = preferences[SERVER_PORT] ?: DEFAULT_PORT
        }
        return port
    }
}
