package com.douyin.downloader.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局存储权限状态。
 * - [hasPermission] 当前是否已授权
 * - [refresh] 由 MainActivity.onResume() 调用，重新查询系统并更新
 * - [changes] Compose 端订阅此 flow 即可知道何时授权状态发生变化
 * - [request] 从任意上下文跳系统设置；调用方传入 Activity 用于 startActivity
 */
@Singleton
class StoragePermissionState @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _granted = MutableStateFlow(check())
    val changes: StateFlow<Boolean> = _granted.asStateFlow()

    fun hasPermission(): Boolean = _granted.value

    fun refresh() {
        val now = check()
        if (now != _granted.value) {
            _granted.value = now
        }
    }

    /**
     * 跳到「所有文件访问权限」系统设置页。
     * 接受任意 Context（推荐 applicationContext），内部会加 NEW_TASK 标志。
     * 返回 true 表示成功发起跳转，false 表示该 ROM 不支持（极少见）。
     */
    fun request(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } else {
            // Android 9 及以下走运行时权限，由 Activity.requestPermissions 处理；
            // 这里只发信号，不直接处理回调。
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (_: Exception) { }
            false
        }
    }

    private fun check(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
