package com.douyin.downloader.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆圆解析 YuanZai · 加载动画
 *
 * - [YuanLoadingDots]：三点脉动（交错 150ms 延迟），按 primary 色渐变。
 * - [YuanLoadingRing]：圆环进度旋转。
 */
@Composable
fun YuanLoadingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    spacing: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phases = listOf(0, 1, 2).map { i ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = i * 150),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot-$i",
        )
    }
    Row(
        modifier = modifier.size((dotSize * 3 + spacing * 2)),
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        phases.forEach { alpha ->
            Canvas(modifier = Modifier.size(dotSize)) {
                drawCircle(color = color.copy(alpha = alpha.value))
            }
        }
    }
}

@Composable
fun YuanLoadingRing(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val transition = rememberInfiniteTransition(label = "ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900)),
        label = "ring-rot",
    )
    Canvas(modifier = modifier.size(size)) {
        val sw = strokeWidth.toPx()
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(sw / 2, sw / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw),
        )
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(sw / 2, sw / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw),
        )
    }
}