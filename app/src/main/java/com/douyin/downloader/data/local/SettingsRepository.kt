package com.douyin.downloader.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户设置：DataStore 持久化。
 *
 * - [defaultQuality]: 默认下载清晰度（auto / highest / lowest）
 * - [maxConcurrent]: 同时下载数（2 / 3 / 4）
 * - [subdirName]: MediaStore.Downloads 下的子目录名
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.dataStore by preferencesDataStore(name = "settings")

    enum class DefaultQuality(val key: String) {
        Auto("auto"),
        Highest("highest"),
        Lowest("lowest");

        companion object {
            fun fromKey(key: String?): DefaultQuality =
                entries.firstOrNull { it.key == key } ?: Auto
        }
    }

    data class Settings(
        val defaultQuality: DefaultQuality = DefaultQuality.Auto,
        val maxConcurrent: Int = 2,
        val subdirName: String = "DYSave",
    )

    private object Keys {
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val SUBDIR_NAME = stringPreferencesKey("subdir_name")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            defaultQuality = DefaultQuality.fromKey(prefs[Keys.DEFAULT_QUALITY]),
            maxConcurrent = (prefs[Keys.MAX_CONCURRENT] ?: 2).coerceIn(1, 6),
            subdirName = prefs[Keys.SUBDIR_NAME]?.takeIf { it.isNotBlank() } ?: "DYSave",
        )
    }

    suspend fun setDefaultQuality(value: DefaultQuality) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = value.key }
    }

    suspend fun setMaxConcurrent(value: Int) {
        context.dataStore.edit { it[Keys.MAX_CONCURRENT] = value.coerceIn(1, 6) }
    }

    suspend fun setSubdirName(value: String) {
        val cleaned = value.trim().ifEmpty { "DYSave" }
        context.dataStore.edit { it[Keys.SUBDIR_NAME] = cleaned }
    }
}
