package com.douyin.downloader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.douyin.downloader.ui.theme.YuanGradientEnd
import com.douyin.downloader.ui.theme.YuanGradientStart

/**
 * 圆圆解析 YuanZai · 卡片
 *
 * 三档强调度：Low（surface 平面）/ Mid（surfaceContainer 提升）/ High（surfaceContainerHigh + 1dp 阴影）。
 * 支持渐变描边（gradientBorder = true）。
 */
enum class YuanCardEmphasis { Low, Mid, High }

@Composable
fun YuanCard(
    modifier: Modifier = Modifier,
    emphasis: YuanCardEmphasis = YuanCardEmphasis.Mid,
    gradientBorder: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val colors: CardColors = when (emphasis) {
        YuanCardEmphasis.Low -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        YuanCardEmphasis.Mid -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        YuanCardEmphasis.High -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    val shape = MaterialTheme.shapes.medium
    val border = if (gradientBorder) {
        BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(listOf(YuanGradientStart, YuanGradientEnd)),
        )
    } else null

    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = if (emphasis == YuanCardEmphasis.High) CardDefaults.elevatedCardElevation() else CardDefaults.cardElevation(),
        border = border,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}
