package com.douyin.downloader.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.douyin.downloader.MainActivity
import com.douyin.downloader.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 抖音 OAuth 授权回调 Activity
 * 在 AndroidManifest.xml 中注册为 douyindownloader://auth
 * 接收 SDK 返回的 auth code 后，传递给 AuthRepository 完成 token 交换
 */
@AndroidEntryPoint
class AuthCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val data = intent?.data
        if (data == null) {
            finish()
            return
        }

        val code = data.getQueryParameter("code")

        if (code.isNullOrEmpty()) {
            finish()
            return
        }

        scope.launch {
            try {
                val clientKey = authRepository.getClientKey() ?: return@launch
                authRepository.exchangeCode(
                    clientKey = clientKey,
                    clientSecret = "",
                    code = code,
                )
            } catch (_: Exception) { }

            val resultIntent = Intent(this@AuthCallbackActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(resultIntent)
            finish()
        }
    }
}