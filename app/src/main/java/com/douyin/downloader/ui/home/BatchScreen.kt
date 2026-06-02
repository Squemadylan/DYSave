package com.douyin.downloader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.douyin.downloader.data.model.ContentInfo

/**
 * 批量下载页。
 *
 * 流程：
 * 1. 文本框粘贴多个抖音链接（一行一条），或粘整段抖音分享文案
 *    （系统会按行切 + 保留看起来像 url 的 token）；
 * 2. 点「解析全部」，并发解析（最多 3 个），单条失败不影响其他；
 * 3. 在列表里勾选要下载的视频/图集/合集；
 * 4. 点「下载所选」按顺序入队。
 */
@Composable
fun BatchScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 错误吐司
    LaunchedEffect(state.error) {
        val err = state.error
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Text(
                    text = "批量下载",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                Text(
                    text = "一行一条粘贴链接，例如：\n" +
                        "https://v.douyin.com/xxx1/\n" +
                        "https://v.douyin.com/xxx2/\n" +
                        "（# 开头视为注释，跳过）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                OutlinedTextField(
                    value = state.batchInput,
                    onValueChange = viewModel::onBatchInputChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp), // 固定高度；OutlinedTextField 多行默认自带垂直滚动
                    placeholder = { Text("粘贴链接…") },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !state.batchIsParsing,
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = viewModel::onBatchParse,
                        enabled = !state.batchIsParsing && state.batchInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (state.batchIsParsing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(state.batchParseMessage.ifEmpty { "解析中..." })
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("解析全部")
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    IconButton(
                        onClick = {
                            val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                            val text = clip?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!text.isNullOrBlank()) {
                                // 智能粘贴：拼到现有内容（换行分隔），不去重
                                val merged = if (state.batchInput.isBlank()) text
                                else state.batchInput.trimEnd() + "\n" + text
                                viewModel.onBatchInputChanged(merged)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "从剪贴板粘贴",
                        )
                    }
                }
            }

            // 解析结果列表
            if (state.batchItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.batchParseMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = viewModel::onBatchSelectAll,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("全选/反选")
                        }
                    }
                }

                items(items = state.batchItems, key = { it.id }) { item ->
                    BatchItemRow(
                        item = item,
                        selected = item.id in state.batchSelectedIds,
                        onToggle = { viewModel.onBatchItemToggle(item.id) },
                    )
                }
            }
        }

        // 底部下载按钮
        if (state.batchItems.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = viewModel::onBatchDownloadSelected,
                    enabled = state.batchSelectedIds.isNotEmpty() && !state.batchIsParsing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("下载所选（${state.batchSelectedIds.size}）")
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data -> Snackbar(snackbarData = data) }
    }
}

@Composable
private fun BatchItemRow(
    item: HomeViewModel.BatchItem,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == HomeViewModel.BatchItem.Status.OK, onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 缩略图 / 状态图标
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    item.status == HomeViewModel.BatchItem.Status.FAILED -> {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    item.contentInfo != null -> {
                        val cover = coverOf(item.contentInfo)
                        if (cover.isNotEmpty()) {
                            AsyncImage(
                                model = cover,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = iconOf(item.contentInfo),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 类型角标
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = iconOf(item.contentInfo),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                when {
                    item.status == HomeViewModel.BatchItem.Status.FAILED -> {
                        Text(
                            text = "解析失败",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = item.error ?: "未知错误",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    item.contentInfo != null -> {
                        Text(
                            text = item.contentInfo.title.ifBlank { "（无标题）" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                        )
                        Text(
                            text = item.contentInfo.author.ifBlank { "未知作者" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = item.rawUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.size(8.dp))

            if (item.status == HomeViewModel.BatchItem.Status.OK) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun coverOf(info: ContentInfo): String = when (info) {
    is ContentInfo.Video -> info.cover
    is ContentInfo.ImageGallery -> info.cover
    is ContentInfo.Animated -> info.cover
}

private fun iconOf(info: ContentInfo): ImageVector = when (info) {
    is ContentInfo.Video -> Icons.Filled.PlayArrow
    is ContentInfo.ImageGallery -> Icons.Filled.Info
    is ContentInfo.Animated -> Icons.Filled.PlayArrow
}
