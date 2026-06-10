package com.douyin.downloader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.douyin.downloader.ui.theme.YuanGradientEnd
import com.douyin.downloader.ui.theme.YuanGradientStart

/**
 * 圆圆解析 YuanZai · 空状态
 *
 * 几何插画（圆 + 弧线） + 主标题 + 副标题 + 可选 CTA。
 * 插画使用 brand 渐变着色，亮 / 暗色通用。
 */
@Composable
fun YuanEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null,
    illustrationVariant: Int = 0,
) {
    val gradient = Brush.linearGradient(
        colors = listOf(YuanGradientStart, YuanGradientEnd),
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val outerR = w * 0.42f
                drawCircle(
                    brush = gradient,
                    radius = outerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 6f),
                )
                drawArc(
                    color = YuanGradientStart,
                    startAngle = 200f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(cx - outerR * 0.6f, cy - outerR * 0.6f),
                    size = Size(outerR * 1.2f, outerR * 1.2f),
                    style = Stroke(width = 10f),
                )
                drawCircle(
                    color = YuanGradientEnd.copy(alpha = 0.6f),
                    radius = 10f,
                    center = Offset(cx + outerR * 0.4f, cy - outerR * 0.5f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (ctaText != null && onCtaClick != null) {
            Spacer(Modifier.height(20.dp))
            YuanButton(text = ctaText, onClick = onCtaClick, style = com.douyin.downloader.ui.components.YuanButtonStyle.Tonal)
        }
    }
}