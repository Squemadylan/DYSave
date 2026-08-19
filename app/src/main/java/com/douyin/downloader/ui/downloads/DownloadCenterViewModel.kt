package com.douyin.downloader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.local.DownloadTaskDao
import com.douyin.downloader.data.local.DownloadTaskEntity
import com.douyin.downloader.data.repository.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 下载中心 ViewModel。
 *
 * 把 [DownloadManager.activeTasks]（活动任务）和 [DownloadTaskDao.getRecent]（历史）
 * 合并成一个对 UI 友好的 [UiState]。
 */
@HiltViewModel
class DownloadCenterViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val downloadTaskDao: DownloadTaskDao,
) : ViewModel() {

    data class UiState(
        val activeTasks: List<DownloadManager.Task> = emptyList(),
        val history: List<DownloadTaskEntity> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        downloadManager.activeTasks,
        downloadTaskDao.getRecent(),
    ) { activeMap, history ->
        UiState(
            activeTasks = activeMap.values.sortedBy { it.id },
            history = history,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    fun onClearDownloadHistory() {
        viewModelScope.launch {
            downloadTaskDao.clearAll()
        }
    }
}
