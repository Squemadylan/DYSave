package com.douyin.downloader.data.repository

import android.net.Uri
import com.douyin.downloader.data.local.DownloadTaskDao
import com.douyin.downloader.data.local.DownloadTaskEntity
import com.douyin.downloader.data.local.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局下载编排器。
 *
 * 职责：
 * - 持有全局并发信号量（默认 2 路同时下载）
 * - 暴露活动任务状态（StateFlow<Set<DownloadTask>>）
 * - 暴露最近一次结果/错误（SharedFlow<DownloadEvent>，供一次性 toast / 通知使用）
 * - 把完成的任务写入 [DownloadTaskDao] 历史表
 *
 * 任何 ViewModel（HomeViewModel、BatchDownloadViewModel、未来的 ProfileViewModel）
 * 都可以通过 `enqueue(...)` 把下载意图投递进来，由本类统一调度。
 *
 * 并发数同步：启动时从 [SettingsRepository] 读一次 DataStore 中的值；之后
 * SettingsRepository 的 flow 一旦变化（含 ProfileViewModel 写入），就立刻重建 Semaphore。
 * 这样无论应用冷启动 / 热启动，maxConcurrent 始终与持久化设置一致。
 */
@Singleton
class DownloadManager @Inject constructor(
    private val downloadTaskDao: DownloadTaskDao,
    settingsRepository: SettingsRepository,
) {
    enum class Status { PENDING, DOWNLOADING, DONE, ERROR }

    data class Task(
        val id: Long,
        val name: String,
        val mimeType: String?,
        val status: Status,
        val progress: Float? = null,
        val uri: String? = null,
        val error: String? = null,
    )

    sealed interface Event {
        data class Completed(val task: Task) : Event
        data class Failed(val task: Task, val message: String) : Event
    }

    private val taskIdCounter = AtomicLong(System.currentTimeMillis())

    private val _activeTasks = MutableStateFlow<Map<Long, Task>>(emptyMap())
    val activeTasks: StateFlow<Map<Long, Task>> = _activeTasks.asStateFlow()

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    // 全局并发控制：同时最多 N 路下载。
    // 跨 ViewModel 共享 —— AppModule 也可注入同一实例。
    // 调整并发数时会原子替换；正在跑的任务继续持有旧 semaphore 引用，自然完成。
    private val semaphoreRef = AtomicReference(Semaphore(permits = 2))

    /** 当前生效的 Semaphore，新任务会取最新值。 */
    val currentSemaphore: Semaphore get() = semaphoreRef.get()

    /** 重新设置并发数。返回是否真的发生了变化。 */
    fun setMaxConcurrent(permits: Int): Boolean {
        val p = permits.coerceIn(1, 6)
        val old = semaphoreRef.get()
        if (old.availablePermits == p) return false
        semaphoreRef.set(Semaphore(permits = p))
        return true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 1) 启动时立刻从 DataStore 同步一次当前并发数（解决"重启后回到默认 2"的 bug）
        scope.launch {
            val initial = settingsRepository.flow.first()
            setMaxConcurrent(initial.maxConcurrent)
        }
        // 2) 持续监听 DataStore 变化（覆盖 ProfileViewModel 写入等路径），重建 Semaphore
        scope.launch {
            settingsRepository.flow.drop(1).collect { settings ->
                setMaxConcurrent(settings.maxConcurrent)
            }
        }
    }

    fun enqueue(
        name: String,
        mimeType: String?,
        block: suspend (taskId: Long, onProgress: (Long, Long?) -> Unit) -> Uri?,
    ) {
        val id = taskIdCounter.incrementAndGet()
        val initial = Task(id = id, name = name, mimeType = mimeType, status = Status.PENDING)
        _activeTasks.update { it + (id to initial) }

        // 取出本任务要用的 semaphore 引用；之后即使 setMaxConcurrent 替换了
        // 全局引用，本任务继续走旧 semaphore，自然完成不会被打断。
        val sem = currentSemaphore
        scope.launch {
            sem.withPermit {
                update(id) { it.copy(status = Status.DOWNLOADING) }
                try {
                    val onProgress: (Long, Long?) -> Unit = { downloaded, total ->
                        if (total != null && total > 0) {
                            update(id) { it.copy(progress = downloaded.toFloat() / total) }
                        }
                    }
                    val uri = block(id, onProgress)
                    val done = _activeTasks.value[id]?.copy(
                        status = Status.DONE,
                        progress = 1f,
                        uri = uri?.toString(),
                    ) ?: return@withPermit
                    // 写历史
                    downloadTaskDao.insert(
                        DownloadTaskEntity(
                            name = name,
                            status = "DONE",
                            uri = uri?.toString(),
                            mimeType = mimeType,
                        ),
                    )
                    // 从活动列表移除
                    _activeTasks.update { it - id }
                    _events.tryEmit(Event.Completed(done))
                } catch (e: Exception) {
                    val msg = e.message ?: "下载失败，请检查网络或存储空间"
                    val failed = _activeTasks.value[id]?.copy(
                        status = Status.ERROR,
                        error = msg,
                    ) ?: return@withPermit
                    downloadTaskDao.insert(
                        DownloadTaskEntity(
                            name = name,
                            status = "ERROR",
                            error = msg,
                            mimeType = mimeType,
                        ),
                    )
                    _activeTasks.update { it - id }
                    _events.tryEmit(Event.Failed(failed, msg))
                }
            }
        }
    }

    private fun update(id: Long, transform: (Task) -> Task) {
        _activeTasks.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to transform(existing))
        }
    }
}
