package com.douyin.downloader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.douyin.downloader.data.local.HistoryEntity
import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.ui.components.YuanAppBar
import com.douyin.downloader.ui.components.YuanButton
import com.douyin.downloader.ui.components.YuanButtonStyle
import com.douyin.downloader.ui.components.YuanCard
import com.douyin.downloader.ui.components.YuanCardEmphasis
import com.douyin.downloader.ui.components.YuanEmptyState
import com.douyin.downloader.ui.components.YuanLoadingDots
import com.douyin.downloader.ui.components.YuanTextField

@Composable
fun HomeScreen(
    sharedUrl: String? = null,
    viewModel: HomeViewModel,
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToBatch: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrEmpty()) {
            viewModel.onPaste(sharedUrl)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        if (state.inputUrl.isEmpty()) {
            val clip = clipboard.getText()?.toString()?.trim()
            if (!clip.isNullOrEmpty() && isLikelyDouyinUrl(clip)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onUrlChanged(clip)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            YuanAppBar(title = "圆圆解析")

            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "把抖音链接粘到这里",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "支持视频 / 图文 / 动图 · 无水印保存到 Downloads",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                YuanTextField(
                    value = state.inputUrl,
                    onValueChange = viewModel::onUrlChanged,
                    placeholder = "https://v.douyin.com/...",
                    leadingIcon = Icons.Default.Link,
                    showClearButton = true,
                    errorText = if (state.error != null) "解析失败，请检查链接" else null,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                    onImeAction = { viewModel.onParse() },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    YuanButton(
                        text = "解析",
                        onClick = { viewModel.onParse() },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (!state.isLoading) Icons.Default.PlayArrow else null,
                        loading = state.isLoading,
                    )
                    YuanButton(
                        text = "粘贴",
                        style = YuanButtonStyle.Tonal,
                        onClick = {
                            val clip = clipboard.getText()?.toString()?.trim()
                            if (!clip.isNullOrEmpty()) viewModel.onPaste(clip)
                        },
                        leadingIcon = Icons.Default.ContentPaste,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = state.isLoading,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        YuanLoadingDots()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.loadingMessage.ifEmpty { "正在解析..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.contentInfo?.let { info ->
                Spacer(Modifier.height(16.dp))
                ResultSection(
                    info = info,
                    state = state,
                    onDownload = {
                        viewModel.onDownloadVideo()
                        onNavigateToDownloads()
                    },
                    onDownloadSelectedImages = {
                        viewModel.onDownloadSelectedImages()
                        onNavigateToDownloads()
                    },
                    onToggleImage = viewModel::onImageToggled,
                    onSelectQuality = viewModel::onQualitySelected,
                    onSynthesize = {
                        viewModel.onSynthesizeVideo()
                        onNavigateToDownloads()
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            HistorySection(
                history = state.history,
                onClick = viewModel::onHistoryItemClicked,
            )

            Spacer(Modifier.height(20.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                YuanButton(
                    text = "一键批量",
                    onClick = onNavigateToBatch,
                    style = YuanButtonStyle.Tonal,
                    leadingIcon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ResultSection(
    info: ContentInfo,
    state: HomeViewModel.UiState,
    onDownload: () -> Unit,
    onDownloadSelectedImages: () -> Unit,
    onToggleImage: (Int) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSynthesize: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        CoverCard(info = info)

        Spacer(Modifier.height(12.dp))

        InfoCard(
            info = info,
            state = state,
            onDownload = onDownload,
            onDownloadSelectedImages = onDownloadSelectedImages,
            onToggleImage = onToggleImage,
            onSelectQuality = onSelectQuality,
            onSynthesize = onSynthesize,
        )
    }
}

@Composable
private fun CoverCard(info: ContentInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(21f / 9f)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (info.cover.isNotEmpty()) {
            AsyncImage(
                model = info.cover,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = typeBadge(info),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun InfoCard(
    info: ContentInfo,
    state: HomeViewModel.UiState,
    onDownload: () -> Unit,
    onDownloadSelectedImages: () -> Unit,
    onToggleImage: (Int) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onSynthesize: () -> Unit,
) {
    YuanCard(emphasis = YuanCardEmphasis.Mid) {
        Text(
            text = info.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (info.author.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "@${info.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        when (info) {
            is ContentInfo.Video -> VideoDownloadSection(
                info = info,
                state = state,
                onSelectQuality = onSelectQuality,
                onDownload = onDownload,
            )
            is ContentInfo.Animated -> AnimatedDownloadSection(
                onDownload = onDownload,
                onSynthesize = onSynthesize,
            )
            is ContentInfo.ImageGallery -> ImageGallerySection(
                info = info,
                state = state,
                onToggleImage = onToggleImage,
                onDownloadSelectedImages = onDownloadSelectedImages,
                onSynthesize = onSynthesize,
            )
        }
    }
}

@Composable
private fun VideoDownloadSection(
    info: ContentInfo.Video,
    state: HomeViewModel.UiState,
    onSelectQuality: (Int) -> Unit,
    onDownload: () -> Unit,
) {
    val qualities = info.qualities
    if (qualities.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            qualities.forEachIndexed { idx, q ->
                FilterChip(
                    selected = idx == state.selectedQualityIndex,
                    onClick = { onSelectQuality(idx) },
                    label = { Text(q.label) },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    YuanButton(
        text = "下载视频",
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = Icons.Default.Download,
    )
}

@Composable
private fun AnimatedDownloadSection(
    onDownload: () -> Unit,
    onSynthesize: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        YuanButton(
            text = "下载动图",
            onClick = onDownload,
            modifier = Modifier.weight(1f),
            leadingIcon = Icons.Default.Download,
        )
        YuanButton(
            text = "合成视频",
            onClick = onSynthesize,
            modifier = Modifier.weight(1f),
            style = YuanButtonStyle.Tonal,
            leadingIcon = Icons.Default.PlayArrow,
        )
    }
}

@Composable
private fun ImageGallerySection(
    info: ContentInfo.ImageGallery,
    state: HomeViewModel.UiState,
    onToggleImage: (Int) -> Unit,
    onDownloadSelectedImages: () -> Unit,
    onSynthesize: () -> Unit,
) {
    ImageSelectionGrid(
        images = info.images,
        selected = state.selectedImages,
        onToggle = onToggleImage,
    )

    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        YuanButton(
            text = "下载选中 (${state.selectedImages.size})",
            onClick = onDownloadSelectedImages,
            modifier = Modifier.weight(1f),
            leadingIcon = Icons.Default.Download,
        )
        YuanButton(
            text = "合成视频",
            onClick = onSynthesize,
            modifier = Modifier.weight(1f),
            style = YuanButtonStyle.Tonal,
            leadingIcon = Icons.Default.PlayArrow,
        )
    }
}

@Composable
private fun ImageSelectionGrid(
    images: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        images.chunked(3).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIdx, url ->
                    val idx = rowIdx * 3 + colIdx
                    val isSelected = idx in selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onToggle(idx) },
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "已选",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "未选",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<HistoryEntity>,
    onClick: (HistoryEntity) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "最近解析",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${history.size} 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            YuanEmptyState(
                title = "还没有解析记录",
                description = "粘贴一个抖音链接开始第一次下载吧",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(history, key = { it.id }) { entity ->
                    HistoryCard(entity = entity, onClick = { onClick(entity) })
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entity: HistoryEntity, onClick: () -> Unit) {
    Box(modifier = Modifier.width(140.dp)) {
        YuanCard(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (entity.cover.isNotEmpty()) {
                    AsyncImage(
                        model = entity.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = entity.title.ifEmpty { "未命名" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entity.author.isNotEmpty()) {
                    Text(
                        text = "@${entity.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun isLikelyDouyinUrl(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("v.douyin.com") || lower.contains("douyin.com/video") || lower.contains("抖音")
}

private fun typeBadge(info: ContentInfo): String = when (info) {
    is ContentInfo.Video -> "视频"
    is ContentInfo.ImageGallery -> "图文"
    is ContentInfo.Animated -> "动图"
}
