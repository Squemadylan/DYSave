package com.douyin.downloader.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全存储用户登录凭证（access_token, refresh_token, open_id 等）
 * 使用 EncryptedSharedPreferences，Android 6.0+ 兼容
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "dy_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_OPEN_ID = "open_id"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_CLIENT_KEY = "client_key"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var openId: String?
        get() = prefs.getString(KEY_OPEN_ID, null)
        set(value) = prefs.edit().putString(KEY_OPEN_ID, value).apply()

    var expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0)
        set(value) = prefs.edit().putLong(KEY_EXPIRES_AT, value).apply()

    var nickname: String?
        get() = prefs.getString(KEY_NICKNAME, null)
        set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

    var avatar: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    var clientKey: String?
        get() = prefs.getString(KEY_CLIENT_KEY, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_KEY, value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrEmpty() && expiresAt > System.currentTimeMillis() / 1000

    val isTokenExpired: Boolean
        get() = expiresAt <= System.currentTimeMillis() / 1000

    fun clear() {
        prefs.edit().clear().apply()
    }
}