package com.droid.dolphy

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object DeadcowBackupManager {
    private const val TAG = "DeadcowBackupManager"
    private const val BACKUP_FILE_NAME = "deadcow_progress_backup.json"
    private const val PREFS_DOLPHIN = "DolphyPassportPrefs"
    private const val PREFS_DOLPHY = "DolphyPrefs"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun autoBackup(context: Context) {
        scope.launch {
            saveBackup(context)
        }
    }

    fun autoRestoreIfEmpty(context: Context) {
        val dolphinPrefs = context.getSharedPreferences(PREFS_DOLPHIN, Context.MODE_PRIVATE)
        val currentIcounter = dolphinPrefs.getInt("icounter", 0)
        val currentPackets = dolphinPrefs.getInt("ble_packets_sent", 0)

        // Only restore if current state is fresh/uninitialized
        if (currentIcounter == 0 && currentPackets == 0) {
            restoreBackup(context)
        }
    }

    fun saveBackup(context: Context): Boolean {
        return runCatching {
            val root = JSONObject()

            // 1. Dolphin / Progression Prefs
            val dolphinPrefs = context.getSharedPreferences(PREFS_DOLPHIN, Context.MODE_PRIVATE)
            val dolphinJson = JSONObject()
            dolphinPrefs.all.forEach { (key, value) ->
                when (value) {
                    is Int -> dolphinJson.put(key, value)
                    is Long -> dolphinJson.put(key, value)
                    is Float -> dolphinJson.put(key, value.toDouble())
                    is Boolean -> dolphinJson.put(key, value)
                    is String -> dolphinJson.put(key, value)
                }
            }
            root.put("dolphin_prefs", dolphinJson)

            // 2. Dolphy / Theme / Config Prefs
            val dolphyPrefs = context.getSharedPreferences(PREFS_DOLPHY, Context.MODE_PRIVATE)
            val dolphyJson = JSONObject()
            dolphyPrefs.all.forEach { (key, value) ->
                when (value) {
                    is Int -> dolphyJson.put(key, value)
                    is Long -> dolphyJson.put(key, value)
                    is Float -> dolphyJson.put(key, value.toDouble())
                    is Boolean -> dolphyJson.put(key, value)
                    is String -> dolphyJson.put(key, value)
                }
            }
            root.put("dolphy_prefs", dolphyJson)
            root.put("timestamp", System.currentTimeMillis())

            val content = root.toString(2)

            // Save to internal storage
            val internalFile = File(context.filesDir, BACKUP_FILE_NAME)
            internalFile.writeText(content)

            // Save to external files dir (persists even if app data is partially cleared)
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir != null) {
                val externalFile = File(externalDir, BACKUP_FILE_NAME)
                externalFile.writeText(content)
            }

            Log.i(TAG, "Deadcow progression and configuration backed up successfully")
            true
        }.getOrElse {
            Log.e(TAG, "Failed to backup Deadcow data", it)
            false
        }
    }

    fun restoreBackup(context: Context): Boolean {
        return runCatching {
            var file = File(context.filesDir, BACKUP_FILE_NAME)
            if (!file.exists() || file.length() == 0L) {
                val extDir = context.getExternalFilesDir(null)
                if (extDir != null) {
                    file = File(extDir, BACKUP_FILE_NAME)
                }
            }
            if (!file.exists() || file.length() == 0L) return false

            val root = JSONObject(file.readText())

            // Restore dolphin_prefs
            if (root.has("dolphin_prefs")) {
                val dJson = root.getJSONObject("dolphin_prefs")
                val editor = context.getSharedPreferences(PREFS_DOLPHIN, Context.MODE_PRIVATE).edit()
                dJson.keys().forEach { key ->
                    when (val v = dJson.get(key)) {
                        is Int -> editor.putInt(key, v)
                        is Long -> editor.putLong(key, v)
                        is Double -> editor.putFloat(key, v.toFloat())
                        is Boolean -> editor.putBoolean(key, v)
                        is String -> editor.putString(key, v)
                    }
                }
                editor.apply()
            }

            // Restore dolphy_prefs (theme, animations, accent)
            if (root.has("dolphy_prefs")) {
                val pJson = root.getJSONObject("dolphy_prefs")
                val editor = context.getSharedPreferences(PREFS_DOLPHY, Context.MODE_PRIVATE).edit()
                pJson.keys().forEach { key ->
                    when (val v = pJson.get(key)) {
                        is Int -> editor.putInt(key, v)
                        is Long -> editor.putLong(key, v)
                        is Double -> editor.putFloat(key, v.toFloat())
                        is Boolean -> editor.putBoolean(key, v)
                        is String -> editor.putString(key, v)
                    }
                }
                editor.apply()
            }

            DolphyRepository.refresh(context)
            Log.i(TAG, "Deadcow progression and configuration restored successfully")
            true
        }.getOrElse {
            Log.e(TAG, "Failed to restore Deadcow data", it)
            false
        }
    }
}
