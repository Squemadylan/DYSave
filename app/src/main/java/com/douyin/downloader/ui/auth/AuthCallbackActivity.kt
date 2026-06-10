package com.douyin.downloader.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.douyin.downloader.MainActivity
import com.douyin.downloader.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val code = intent?.data?.getQueryParameter("code")
        if (code.isNullOrEmpty()) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val clientKey = authRepository.getClientKey() ?: return@launch
                authRepository.exchangeCode(
                    clientKey = clientKey,
                    clientSecret = "",
                    code = code,
                )
            } catch (_: Exception) {
                // token 交换失败时仍回到主页，AuthViewModel 会显示未登录状态
            }

            startActivity(
                Intent(this@AuthCallbackActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
            finish()
        }
    }
}
