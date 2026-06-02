package com.douyin.downloader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.local.SettingsRepository
import com.douyin.downloader.data.repository.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
}
