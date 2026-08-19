package com.douyin.downloader.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.douyin.downloader.BuildConfig
import com.douyin.downloader.data.local.SettingsRepository
import com.douyin.downloader.ui.components.YuanButton
import com.douyin.downloader.ui.components.YuanButtonStyle
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { SectionTitle("我的") }

        // 1) 默认清晰度
        item {
            SettingCard(
                title = "默认清晰度",
                subtitle = "下载视频时优先使用的画质；解析后可手动切换",
                icon = Icons.Filled.Star,
            ) {
                DefaultQualitySegmented(
                    current = settings.defaultQuality,
                    onChange = viewModel::onDefaultQualityChanged,
                )
            }
        }

        // 2) 同时下载数
        item {
            SettingCard(
                title = "同时下载数",
                subtitle = "并发执行下载任务的数量；调整后立即生效",
                icon = Icons.Filled.Settings,
            ) {
                ConcurrencySegmented(
                    current = settings.maxConcurrent,
                    onChange = viewModel::onMaxConcurrentChanged,
                )
            }
        }

        // 3) 下载子目录
        item {
            SettingCard(
                title = "下载子目录",
                subtitle = "在系统下载目录下的子文件夹名；重启后生效",
                icon = Icons.Filled.Create,
            ) {
                SubdirEditor(
                    current = settings.subdirName,
                    onChange = viewModel::onSubdirNameChanged,
                )
            }
        }

        // 4) 小红书 Cookie
        item {
            val context = LocalContext.current
            val loginLauncher = rememberCookieLoginLauncher()
            SettingCard(
                title = "小红书 Cookie",
                subtitle = "解析小红书需要登录态；可一键登录获取，或从浏览器复制 Cookie 粘贴。",
                icon = Icons.Filled.Create,
            ) {
                CookieEditor(
                    savedCookie = settings.xhsCookie,
                    onSave = viewModel::onXhsCookieSaved,
                    onClear = viewModel::onXhsCookieCleared,
                    onLogin = { loginLauncher.launch(Intent(context, XhsLoginActivity::class.java)) },
                    autoFetch = { viewModel.fetchXhsCookieViaRoot() },
                    errorProvider = { viewModel.lastRootError() },
                    placeholder = "或粘贴 Cookie 原文",
                    successToast = "已获取小红书 Cookie",
                    errorFallback = "获取失败：请确认已授予 Root 权限，且已在「登录获取」完成一次小红书登录",
                )
            }
        }

        // 4.5) 抖音 Cookie（选填，缓解 WAF / 限流）
        item {
            val context = LocalContext.current
            val douyinLoginLauncher = rememberCookieLoginLauncher()
            SettingCard(
                title = "抖音 Cookie（选填）",
                subtitle = "带登录态可显著提高被 WAF / 限流的视频解析成功率；可一键登录或粘贴浏览器 Cookie。",
                icon = Icons.Filled.Create,
            ) {
                CookieEditor(
                    savedCookie = settings.douyinCookie,
                    onSave = viewModel::onDouyinCookieSaved,
                    onClear = viewModel::onDouyinCookieCleared,
                    onLogin = { douyinLoginLauncher.launch(Intent(context, DouyinLoginActivity::class.java)) },
                    autoFetch = { viewModel.fetchDouyinCookieViaRoot() },
                    errorProvider = { viewModel.lastRootError() },
                    placeholder = "或粘贴 Cookie 原文（从浏览器 F12 复制）",
                    successToast = "已获取抖音 Cookie",
                    errorFallback = "获取失败：请确认已授予 Root 权限，且抖音 App 已登录",
                )
            }
        }

        // 5) 关于
        item {
            AboutCard()
        }
    }
}

// ----------------- 区块组件 -----------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun DefaultQualitySegmented(
    current: SettingsRepository.DefaultQuality,
    onChange: (SettingsRepository.DefaultQuality) -> Unit,
) {
    val options = listOf(
        "自动" to SettingsRepository.DefaultQuality.Auto,
        "最高" to SettingsRepository.DefaultQuality.Highest,
        "最低" to SettingsRepository.DefaultQuality.Lowest,
    )
    SegmentedRow(options, current) { onChange(it) }
}

@Composable
private fun ConcurrencySegmented(
    current: Int,
    onChange: (Int) -> Unit,
) {
    val options = listOf("2" to 2, "3" to 3, "4" to 4)
    SegmentedRow(options, current) { onChange(it) }
}

@Composable
private fun <T> SegmentedRow(
    options: List<Pair<String, T>>,
    current: T,
    onChange: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, value) ->
            SegmentedButton(
                selected = value == current,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 通用 Cookie 编辑器，小红书 / 抖音复用同一套 UI：
 * 「登录获取」「Root 一键获取」「粘贴文本框」「保存 / 清空」。
 *
 * @param placeholder   文本框占位符
 * @param successToast Root 一键获取成功时的提示文案
 * @param errorFallback Root 一键获取失败且无更具体错误时的兜底文案
 */
@Composable
private fun CookieEditor(
    savedCookie: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onLogin: () -> Unit,
    autoFetch: suspend () -> String?,
    errorProvider: () -> String? = { null },
    placeholder: String,
    successToast: String,
    errorFallback: String,
) {
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    YuanButton(
        text = "登录获取",
        onClick = onLogin,
        modifier = Modifier.fillMaxWidth(),
        style = YuanButtonStyle.Tonal,
    )
    Spacer(Modifier.height(8.dp))
    YuanButton(
        text = "Root 一键获取",
        onClick = {
            scope.launch {
                loading = true
                error = null
                val result = autoFetch()
                loading = false
                if (result != null) {
                    text = result
                    onSave(result)
                    Toast.makeText(context, successToast, Toast.LENGTH_SHORT).show()
                } else {
                    error = errorProvider() ?: errorFallback
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        style = YuanButtonStyle.Primary,
        loading = loading,
    )
    if (error != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = error!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 5,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text(placeholder) },
    )
    if (savedCookie.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "已配置",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { onSave(text) },
            enabled = text.isNotBlank(),
        ) {
            Text("保存")
        }
        TextButton(
            onClick = {
                text = ""
                onClear()
            },
            enabled = savedCookie.isNotBlank() || text.isNotBlank(),
        ) {
            Text("清空")
        }
    }
}

/**
 * 登录页（Xhs / Douyin LoginActivity）通过 ActivityResult 把 Cookie 写回 DataStore，
 * 这里只需启动，无需处理返回结果——settings Flow 会自动刷新「已配置」状态。
 */
@Composable
private fun rememberCookieLoginLauncher(): ActivityResultLauncher<Intent> {
    return rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Cookie 已由对应 LoginActivity 写入 DataStore；settings Flow 会自动刷新「已配置」状态
    }
}

@Composable
private fun SubdirEditor(current: String, onChange: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            prefix = { Text("Download/") },
        )
        Spacer(Modifier.size(8.dp))
        TextButton(
            onClick = { onChange(text) },
            enabled = text.isNotBlank() && text != current,
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("关于", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "圆圆解析 · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("应用信息（系统设置）")
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
