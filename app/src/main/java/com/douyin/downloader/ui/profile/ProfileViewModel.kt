package com.douyin.downloader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.local.SettingsRepository
import com.douyin.downloader.data.remote.DouyinCookieHelper
import com.douyin.downloader.data.remote.DouyinCookieManager
import com.douyin.downloader.data.remote.RootCookieFetcher
import com.douyin.downloader.data.remote.XhsCookieHelper
import com.douyin.downloader.data.repository.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 设置 / 我的页 ViewModel。
 *
 * 本应用不申请抖音开放平台 API（个人开发者无权限），不接入 OAuth 登录。
 * 全部下载能力都走"未登录 + 纯 HTML 抓取"路径。
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val downloadManager: DownloadManager,
    private val douyinCookieManager: DouyinCookieManager,
    private val rootCookieFetcher: RootCookieFetcher,
) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> = settingsRepository.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsRepository.Settings(),
    )

    fun onDefaultQualityChanged(value: SettingsRepository.DefaultQuality) {
        viewModelScope.launch { settingsRepository.setDefaultQuality(value) }
    }

    fun onMaxConcurrentChanged(value: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxConcurrent(value)
            downloadManager.setMaxConcurrent(value)
        }
    }

    fun onSubdirNameChanged(value: String) {
        viewModelScope.launch { settingsRepository.setSubdirName(value) }
    }

    fun onXhsCookieSaved(value: String) {
        viewModelScope.launch { settingsRepository.setXhsCookie(value) }
    }

    fun onXhsCookieCleared() {
        viewModelScope.launch {
            settingsRepository.setXhsCookie("")
            withContext(Dispatchers.Main) {
                XhsCookieHelper.clearXhsCookies()
            }
        }
    }

    fun onDouyinCookieSaved(value: String) {
        viewModelScope.launch { settingsRepository.setDouyinCookie(value) }
    }

    fun onDouyinCookieCleared() {
        viewModelScope.launch {
            settingsRepository.setDouyinCookie("")
            withContext(Dispatchers.Main) {
                DouyinCookieHelper.clearDouyinCookies()
            }
        }
    }

    // ===== Cookie 状态查询 =====

    /** 检查抖音 Cookie 是否完整 */
    fun getDouyinCookieStatus(): String {
        val cookie = douyinCookieManager.getCookie()
        return douyinCookieManager.getCookieStatus(cookie)
    }

    /** 检查抖音 Cookie 是否即将过期 */
    fun isDouyinCookieExpiring(): Boolean {
        val cookie = douyinCookieManager.getCookie()
        return douyinCookieManager.isExpiringSoon(cookie)
    }

    /** 获取当前抖音 Cookie 完整度 */
    fun getDouyinCookieCompleteness(): Int {
        val cookie = douyinCookieManager.getCookie()
        return douyinCookieManager.getCookieCompleteness(cookie)
    }

    /** 检查是否已登录抖音 */
    fun isDouyinLoggedIn(): Boolean {
        val cookie = douyinCookieManager.getCookie()
        return douyinCookieManager.isLoggedIn(cookie)
    }

    // ===== Root 一键获取 Cookie =====

    /** 设备是否已授予本 App Root 权限 */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        rootCookieFetcher.hasRoot()
    }

    /** Root 一键获取抖音 Cookie（读抖音 App 的 WebView） */
    suspend fun fetchDouyinCookieViaRoot(): String? = withContext(Dispatchers.IO) {
        rootCookieFetcher.fetchDouyinCookie()
    }

    /** Root 一键获取小红书 Cookie（读本 App 的 WebView） */
    suspend fun fetchXhsCookieViaRoot(): String? = withContext(Dispatchers.IO) {
        rootCookieFetcher.fetchXhsCookie()
    }

    /** 最近一次 Root 获取失败的具体原因（供 UI Toast 展示） */
    fun lastRootError(): String? = rootCookieFetcher.lastError
}
