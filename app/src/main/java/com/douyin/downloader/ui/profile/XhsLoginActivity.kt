package com.douyin.downloader.ui.profile

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.douyin.downloader.data.local.SettingsRepository
import com.douyin.downloader.data.remote.XhsCookieHelper
import com.douyin.downloader.ui.theme.DouyinDownloaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 小红书网页登录：检测到 web_session 后自动保存 Cookie 并关闭。
 */
@AndroidEntryPoint
class XhsLoginActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var saved = false
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CookieManager.getInstance().setAcceptCookie(true)

        setContent {
            DouyinDownloaderTheme {
                XhsLoginScreen(
                    onBack = { finish() },
                    onWebViewCreated = { startPolling() },
                    onPageFinished = { tryCapture() },
                )
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive && !saved) {
                tryCapture()
                delay(800)
            }
        }
    }

    private fun tryCapture() {
        if (saved) return
        val cookie = XhsCookieHelper.readCookie()
        if (!XhsCookieHelper.looksLoggedIn(cookie)) return
        saved = true
        pollJob?.cancel()
        lifecycleScope.launch {
            settingsRepository.setXhsCookie(cookie)
            Toast.makeText(this@XhsLoginActivity, "已获取小红书 Cookie", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XhsLoginScreen(
    onBack: () -> Unit,
    onWebViewCreated: () -> Unit,
    onPageFinished: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var title by remember { mutableStateOf("登录小红书") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (progress in 0f..<1f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AndroidView(
                factory = { context ->
                    @SuppressLint("SetJavaScriptEnabled")
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        // 手机 UA：保持与抖音登录页一致，确保小红书 passport 正常渲染、验证码可获取
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 6 Build/UPB3.230720.019) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }

                            override fun onReceivedTitle(view: WebView?, t: String?) {
                                if (!t.isNullOrBlank()) title = t
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                onPageFinished()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                val host = request?.url?.host ?: return false
                                // 所有 xiaohongshu.com 子域都留在 WebView 内，避免跳转被系统默认处理打断登录流
                                if (host == "xiaohongshu.com" || host.endsWith(".xiaohongshu.com")) {
                                    view?.loadUrl(request.url.toString())
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(XhsCookieHelper.HOME_URL)
                        onWebViewCreated()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
