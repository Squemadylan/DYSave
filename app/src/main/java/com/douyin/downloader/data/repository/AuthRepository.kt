package com.douyin.downloader.data.repository

import com.douyin.downloader.data.local.SessionManager
import com.douyin.downloader.data.model.DownloadException
import com.douyin.downloader.data.remote.DouyinOpenApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 抖音登录与凭证管理仓库
 *
 * 支持两种模式：
 * 1. 游客模式（无需登录）- 使用现有的 HTML 解析逻辑
 * 2. OAuth 模式 - 用户授权后通过 Open API 获取更高质量的数据
 */
@Singleton
class AuthRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val openApi: DouyinOpenApi,
) {
    val isLoggedIn: Boolean get() = sessionManager.isLoggedIn
    val openId: String? get() = sessionManager.openId
    val nickname: String? get() = sessionManager.nickname
    val avatar: String? get() = sessionManager.avatar

    /**
     * 通过授权码（code）换取 access_token
     * code 由 DouyinOpenSDK.authorize() 获取
     */
    suspend fun exchangeCode(
        clientKey: String,
        clientSecret: String,
        code: String,
    ) {
        val resp = openApi.exchangeAccessToken(clientKey, clientSecret, code)
        sessionManager.accessToken = resp.accessToken
        sessionManager.refreshToken = resp.refreshToken
        sessionManager.openId = resp.openId
        sessionManager.expiresAt = System.currentTimeMillis() / 1000 + resp.expiresIn
        sessionManager.clientKey = clientKey
    }

    /**
     * 刷新 access_token
     */
    suspend fun refreshToken(clientKey: String, clientSecret: String): Boolean {
        val refreshToken = sessionManager.refreshToken ?: return false
        return try {
            val resp = openApi.refreshAccessToken(clientKey, clientSecret, refreshToken)
            sessionManager.accessToken = resp.accessToken
            sessionManager.refreshToken = resp.refreshToken
            sessionManager.expiresAt = System.currentTimeMillis() / 1000 + resp.expiresIn
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取用户信息（需已登录）
     */
    suspend fun fetchUserInfo(): Boolean {
        val token = sessionManager.accessToken ?: return false
        return try {
            val resp = openApi.getUserInfo(token)
            sessionManager.nickname = resp.nickname
            sessionManager.avatar = resp.avatar
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 登出，清除所有凭证
     */
    fun logout() {
        sessionManager.clear()
    }

    /**
     * 检查 token 是否过期，若过期尝试自动刷新
     */
    suspend fun ensureValidToken(clientKey: String, clientSecret: String): String? {
        if (sessionManager.isTokenExpired) {
            val ok = refreshToken(clientKey, clientSecret)
            if (!ok) return null
        }
        return sessionManager.accessToken
    }

    fun saveClientKey(clientKey: String) {
        sessionManager.clientKey = clientKey
    }

    fun getClientKey(): String? = sessionManager.clientKey
}