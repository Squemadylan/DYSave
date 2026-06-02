package com.douyin.downloader.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.douyin.downloader.BuildConfig
import com.douyin.downloader.data.local.SettingsRepository

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

        // 4) 关于
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
