package com.douyin.downloader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.douyin.downloader.ui.theme.YuanPillShape

/**
 * 圆圆解析 YuanZai · 按钮
 *
 * 四种风格：Primary（pill 主色）/ Tonal（pill 容器色）/ Outlined（12dp 圆角描边）/ Text（纯文字）。
 * 统一支持 leadingIcon、loading 态、Haptic 反馈。
 */
enum class YuanButtonStyle { Primary, Tonal, Outlined, Text }

@Composable
fun YuanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: YuanButtonStyle = YuanButtonStyle.Primary,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    haptic: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val wrappedOnClick: () -> Unit = {
        if (haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }

    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = loading,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "btn-spinner",
            ) { isLoading ->
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else if (leadingIcon != null) {
                    Icon(imageVector = leadingIcon, contentDescription = null)
                } else {
                    Spacer(Modifier.size(0.dp))
                }
            }
            if (leadingIcon != null || loading) {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(imageVector = trailingIcon, contentDescription = null)
            }
        }
    }

    when (style) {
        YuanButtonStyle.Primary -> Button(
            onClick = wrappedOnClick,
            enabled = enabled && !loading,
            shape = YuanPillShape,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = modifier,
        ) { content() }
        YuanButtonStyle.Tonal -> FilledTonalButton(
            onClick = wrappedOnClick,
            enabled = enabled && !loading,
            shape = YuanPillShape,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            modifier = modifier,
        ) { content() }
        YuanButtonStyle.Outlined -> OutlinedButton(
            onClick = wrappedOnClick,
            enabled = enabled && !loading,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = modifier,
        ) { content() }
        YuanButtonStyle.Text -> TextButton(
            onClick = wrappedOnClick,
            enabled = enabled && !loading,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = modifier,
        ) { content() }
    }
}
