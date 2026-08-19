package com.douyin.downloader.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆圆解析 YuanZai · 形状 tokens
 *
 * 圆角阶梯：extraSmall=8 / small=12 / medium=16 / large=20 / extraLarge=28
 * 按钮 / FAB 走 pill 形态（在组件内单独定义，不进 Shapes）。
 */
val YuanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Pill 形态：50% 圆角，用于主按钮 / 解析按钮。 */
val YuanPillShape = RoundedCornerShape(CornerSize(50))
