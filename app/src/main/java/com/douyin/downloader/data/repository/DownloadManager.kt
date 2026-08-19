package com.douyin.downloader.data.repository

import android.net.Uri
import com.douyin.downloader.data.local.DownloadTaskDao
import com.douyin.downloader.data.local.DownloadTaskEntity
import com.douyin.downloader.data.local.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val downloadTaskDao: DownloadTaskDao,
    settingsRepository: SettingsRepository,
) {
    enum class Status {
        PENDING,
        RUNNING,
        PAUSED,
        DONE,
        ERROR,
    }

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

    private val maxConcurrentRef = AtomicLong(2)

    private val pendingQueue = ArrayDeque<Long>()

    private val taskJobs = AtomicReference<Map<Long, Job>>(emptyMap())

    private val taskPauseFlags = AtomicReference<Map<Long, Boolean>>(emptyMap())

    private val taskBlocks = AtomicReference<Map<Long, suspend (Long, (Long, Long?) -> Unit, () -> Boolean) -> Uri?>>(emptyMap())

    private val taskMutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val initial = settingsRepository.flow.first()
            maxConcurrentRef.set(initial.maxConcurrent.coerceIn(1, 6).toLong())
        }
        scope.launch {
            settingsRepository.flow.drop(1).collect { settings ->
                val newMax = settings.maxConcurrent.coerceIn(1, 6)
                adjustConcurrency(newMax)
            }
        }
    }

    fun setMaxConcurrent(permits: Int) {
        val newMax = permits.coerceIn(1, 6)
        adjustConcurrency(newMax)
    }

    private fun adjustConcurrency(newMax: Int) {
        val oldMax = maxConcurrentRef.getAndSet(newMax.toLong()).toInt()
        if (oldMax == newMax) return

        scope.launch {
            taskMutex.lock()
            try {
                if (newMax > oldMax) {
                    val runningCount = _activeTasks.value.values.count { it.status == Status.RUNNING }
                    val toStart = minOf(newMax - runningCount, pendingQueue.size)
                    repeat(toStart) {
                        val taskId = pendingQueue.removeFirstOrNull() ?: return@repeat
                        startTask(taskId)
                    }
                } else {
                    val toPause = oldMax - newMax
                    pauseRunningTasks(toPause)
                }
            } finally {
                taskMutex.unlock()
            }
        }
    }

    private suspend fun pauseRunningTasks(count: Int) {
        var remaining = count
        val runningTasks = _activeTasks.value.filter { it.value.status == Status.RUNNING }
        for ((taskId, _) in runningTasks) {
            if (remaining <= 0) break
            val currentFlags = taskPauseFlags.get().toMutableMap()
            currentFlags[taskId] = true
            taskPauseFlags.set(currentFlags)
            updateTaskState(taskId, Status.PAUSED)
            remaining--
        }
    }

    private fun updateTaskState(taskId: Long, status: Status) {
        _activeTasks.update { current ->
            val existing = current[taskId] ?: return@update current
            current + (taskId to existing.copy(status = status))
        }
    }

    fun enqueue(
        name: String,
        mimeType: String?,
        block: suspend (taskId: Long, onProgress: (Long, Long?) -> Unit, isPaused: () -> Boolean) -> Uri?,
    ) {
        val id = taskIdCounter.incrementAndGet()
        val initial = Task(id = id, name = name, mimeType = mimeType, status = Status.PENDING)
        _activeTasks.update { it + (id to initial) }

        taskPauseFlags.set(taskPauseFlags.get() + (id to false))
        taskBlocks.set(taskBlocks.get() + (id to block))

        scope.launch {
            taskMutex.lock()
            try {
                val currentMax = maxConcurrentRef.get().toInt()
                val runningCount = _activeTasks.value.values.count { it.status == Status.RUNNING }

                if (runningCount < currentMax) {
                    startTask(id)
                } else {
                    pendingQueue.addLast(id)
                }
            } finally {
                taskMutex.unlock()
            }
        }
    }

    private fun startTask(taskId: Long) {
        pendingQueue.remove(taskId)

        val job = scope.launch {
            executeTask(taskId)
        }

        val jobs = taskJobs.get().toMutableMap()
        jobs[taskId] = job
        taskJobs.set(jobs)

        updateTaskState(taskId, Status.RUNNING)
    }

    private suspend fun executeTask(taskId: Long) {
        val task = _activeTasks.value[taskId] ?: return
        val name = task.name
        val mimeType = task.mimeType
        val block = taskBlocks.get()[taskId] ?: return

        val onProgress: (Long, Long?) -> Unit = { downloaded, total ->
            if (total != null && total > 0) {
                val paused = taskPauseFlags.get()[taskId] == true
                if (!paused) {
                    _activeTasks.update { current ->
                        val existing = current[taskId] ?: return@update current
                        current + (taskId to existing.copy(progress = downloaded.toFloat() / total))
                    }
                }
            }
        }

        val isPaused: () -> Boolean = { taskPauseFlags.get()[taskId] == true }

        while (isPaused()) {
            delay(100)
            val done = _activeTasks.value[taskId]
            if (done == null || done.status != Status.PAUSED) return
        }

        try {
            val uri = block(taskId, onProgress, isPaused)

            val done = _activeTasks.value[taskId]?.copy(
                status = Status.DONE,
                progress = 1f,
                uri = uri?.toString(),
            )
            if (done != null) {
                downloadTaskDao.insert(
                    DownloadTaskEntity(
                        name = name,
                        status = "DONE",
                        uri = uri?.toString(),
                        mimeType = mimeType,
                    ),
                )
                _activeTasks.update { it - taskId }
                _events.tryEmit(Event.Completed(done))
            }

        } catch (e: Exception) {
            if (e is CancellationException) {
                handleTaskCancelled(taskId, name, mimeType)
                return
            }

            val msg = e.message ?: "下载失败，请检查网络或存储空间"
            val failed = _activeTasks.value[taskId]?.copy(
                status = Status.ERROR,
                error = msg,
            )
            if (failed != null) {
                downloadTaskDao.insert(
                    DownloadTaskEntity(
                        name = name,
                        status = "ERROR",
                        error = msg,
                        mimeType = mimeType,
                    ),
                )
                _activeTasks.update { it - taskId }
                _events.tryEmit(Event.Failed(failed, msg))
            }
        } finally {
            cleanupTask(taskId)
            dispatchNextTask()
        }
    }

    private fun handleTaskCancelled(taskId: Long, name: String, mimeType: String?) {
        _activeTasks.update { it - taskId }
        cleanupTask(taskId)
    }

    private fun cleanupTask(taskId: Long) {
        val jobs = taskJobs.get().toMutableMap()
        jobs.remove(taskId)
        taskJobs.set(jobs)

        val flags = taskPauseFlags.get().toMutableMap()
        flags.remove(taskId)
        taskPauseFlags.set(flags)

        val blocks = taskBlocks.get().toMutableMap()
        blocks.remove(taskId)
        taskBlocks.set(blocks)
    }

    private fun dispatchNextTask() {
        scope.launch {
            taskMutex.lock()
            try {
                val currentMax = maxConcurrentRef.get().toInt()
                val runningCount = _activeTasks.value.values.count { it.status == Status.RUNNING }

                if (runningCount < currentMax) {
                    val nextTaskId = pendingQueue.removeFirstOrNull()
                    if (nextTaskId != null) {
                        startTask(nextTaskId)
                    }
                }
            } finally {
                taskMutex.unlock()
            }
        }
    }

    fun cancelTask(taskId: Long) {
        scope.launch {
            taskMutex.lock()
            try {
                pendingQueue.remove(taskId)
                val job = taskJobs.get()[taskId]
                job?.cancel()
                cleanupTask(taskId)
                _activeTasks.update { it - taskId }
            } finally {
                taskMutex.unlock()
            }
        }
    }
}
