package com.tailsync.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tailsync_settings")

data class ClipboardHistoryItem(
    val text: String,
    val timestamp: Long,
    val source: String  // "server" or "phone"
)

class SettingsRepository(private val context: Context) {

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val SERVER_PORT = intPreferencesKey("server_port")
        private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val LAST_SYNCED_TEXT = stringPreferencesKey("last_synced_text")
        private val LAST_SYNCED_TIME = longPreferencesKey("last_synced_time")
        private val CLIPBOARD_HISTORY = stringPreferencesKey("clipboard_history")

        const val DEFAULT_PORT = 8765
        const val DEFAULT_URL = ""
        const val MAX_HISTORY_ITEMS = 5
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

    val clipboardHistory: Flow<List<ClipboardHistoryItem>> = context.dataStore.data.map { preferences ->
        val json = preferences[CLIPBOARD_HISTORY] ?: "[]"
        parseHistoryJson(json)
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

    suspend fun addToHistory(text: String, source: String) {
        // Skip empty or blank text
        if (text.isBlank()) return
        
        context.dataStore.edit { preferences ->
            val existingJson = preferences[CLIPBOARD_HISTORY] ?: "[]"
            val history = parseHistoryJson(existingJson).toMutableList()
            
            // Add new item at the beginning
            val newItem = ClipboardHistoryItem(
                text = text,
                timestamp = System.currentTimeMillis(),
                source = source
            )
            
            // Remove duplicates
            history.removeAll { it.text == text }
            
            // Add to front
            history.add(0, newItem)
            
            // Keep only last MAX_HISTORY_ITEMS
            val trimmed = history.take(MAX_HISTORY_ITEMS)
            
            // Save as JSON
            preferences[CLIPBOARD_HISTORY] = historyToJson(trimmed)
            
            // Also update last synced for backward compatibility
            preferences[LAST_SYNCED_TEXT] = text
            preferences[LAST_SYNCED_TIME] = System.currentTimeMillis()
        }
    }

    private fun parseHistoryJson(json: String): List<ClipboardHistoryItem> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ClipboardHistoryItem(
                    text = obj.getString("text"),
                    timestamp = obj.getLong("timestamp"),
                    source = obj.optString("source", "server")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun historyToJson(history: List<ClipboardHistoryItem>): String {
        val array = JSONArray()
        history.forEach { item ->
            val obj = JSONObject().apply {
                put("text", item.text)
                put("timestamp", item.timestamp)
                put("source", item.source)
            }
            array.put(obj)
        }
        return array.toString()
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
