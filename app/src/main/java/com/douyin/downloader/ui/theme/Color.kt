package com.douyin.downloader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 圆圆解析 YuanZai · 品牌色板
 *
 * 命名沿用 Material 3 ColorScheme 槽位：primary / secondary / tertiary / error / surface
 * 以及三个 Container 与对应的 on- 颜色。Dark 模式在亮色基础上对每个主色做"提亮 + 降饱和"处理，
 * 保证 OLED 真黑底上文字对比度 ≥ 4.5:1。
 *
 * 渐变 Brand Gradient：0xFF0F766E → 0xFF5B5F97，用于首屏 Hero 与封面角标。
 */

// 品牌兜底 · Light
val YuanPrimaryLight            = Color(0xFF0F766E)
val YuanOnPrimaryLight          = Color(0xFFFFFFFF)
val YuanPrimaryContainerLight   = Color(0xFF9EF0E2)
val YuanOnPrimaryContainerLight = Color(0xFF00201C)

val YuanSecondaryLight          = Color(0xFF5B5F97)
val YuanOnSecondaryLight        = Color(0xFFFFFFFF)
val YuanSecondaryContainerLight = Color(0xFFE1E0FF)
val YuanOnSecondaryContainerLight = Color(0xFF161B5F)

val YuanTertiaryLight           = Color(0xFFB45309)
val YuanOnTertiaryLight         = Color(0xFFFFFFFF)
val YuanTertiaryContainerLight  = Color(0xFFFFDDB7)
val YuanOnTertiaryContainerLight = Color(0xFF2C1700)

val YuanErrorLight              = Color(0xFFBA1A1A)
val YuanOnErrorLight            = Color(0xFFFFFFFF)
val YuanErrorContainerLight     = Color(0xFFFFDAD6)
val YuanOnErrorContainerLight   = Color(0xFF410002)

val YuanBackgroundLight         = Color(0xFFFBFDFB)
val YuanOnBackgroundLight       = Color(0xFF171D1B)
val YuanSurfaceLight            = Color(0xFFFBFDFB)
val YuanOnSurfaceLight          = Color(0xFF171D1B)

val YuanSurfaceVariantLight     = Color(0xFFDBE5E0)
val YuanOnSurfaceVariantLight   = Color(0xFF3F4946)
val YuanOutlineLight            = Color(0xFF6F7976)
val YuanOutlineVariantLight     = Color(0xFFBEC9C4)

val YuanInverseSurfaceLight     = Color(0xFF2B3230)
val YuanInverseOnSurfaceLight   = Color(0xFFECF2EF)
val YuanInversePrimaryLight     = Color(0xFF82D4C3)
val YuanSurfaceTintLight        = Color(0xFF0F766E)
val YuanScrimLight              = Color(0xFF000000)

val YuanSurfaceDimLight         = Color(0xFFDBE4E0)
val YuanSurfaceBrightLight      = Color(0xFFFBFDFB)
val YuanSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val YuanSurfaceContainerLowLight    = Color(0xFFF5F8F6)
val YuanSurfaceContainerLight       = Color(0xFFEEF2EF)
val YuanSurfaceContainerHighLight   = Color(0xFFE9ECE9)
val YuanSurfaceContainerHighestLight = Color(0xFFE3E7E4)

// 品牌兜底 · Dark
val YuanPrimaryDark             = Color(0xFF5FD3C2)
val YuanOnPrimaryDark           = Color(0xFF003731)
val YuanPrimaryContainerDark    = Color(0xFF1A4E47)
val YuanOnPrimaryContainerDark  = Color(0xFF9EF0E2)

val YuanSecondaryDark           = Color(0xFFC2C4F0)
val YuanOnSecondaryDark         = Color(0xFF2A2E63)
val YuanSecondaryContainerDark  = Color(0xFF42467B)
val YuanOnSecondaryContainerDark = Color(0xFFE1E0FF)

val YuanTertiaryDark            = Color(0xFFFFB68A)
val YuanOnTertiaryDark          = Color(0xFF4A2800)
val YuanTertiaryContainerDark   = Color(0xFF6B3D0A)
val YuanOnTertiaryContainerDark = Color(0xFFFFDDB7)

val YuanErrorDark               = Color(0xFFFFB4AB)
val YuanOnErrorDark             = Color(0xFF690005)
val YuanErrorContainerDark      = Color(0xFF93000A)
val YuanOnErrorContainerDark    = Color(0xFFFFDAD6)

val YuanBackgroundDark          = Color(0xFF0F1413)
val YuanOnBackgroundDark        = Color(0xFFE0E4E1)
val YuanSurfaceDark             = Color(0xFF0F1413)
val YuanOnSurfaceDark           = Color(0xFFE0E4E1)

val YuanSurfaceVariantDark      = Color(0xFF3F4946)
val YuanOnSurfaceVariantDark    = Color(0xFFBFC9C5)
val YuanOutlineDark             = Color(0xFF899491)
val YuanOutlineVariantDark      = Color(0xFF3F4946)

val YuanInverseSurfaceDark      = Color(0xFFE0E4E1)
val YuanInverseOnSurfaceDark    = Color(0xFF2B3230)
val YuanInversePrimaryDark      = Color(0xFF0F766E)
val YuanSurfaceTintDark         = Color(0xFF5FD3C2)
val YuanScrimDark               = Color(0xFF000000)

val YuanSurfaceDimDark          = Color(0xFF0F1413)
val YuanSurfaceBrightDark       = Color(0xFF353A38)
val YuanSurfaceContainerLowestDark = Color(0xFF090F0E)
val YuanSurfaceContainerLowDark    = Color(0xFF171D1B)
val YuanSurfaceContainerDark       = Color(0xFF1B2120)
val YuanSurfaceContainerHighDark   = Color(0xFF252B2A)
val YuanSurfaceContainerHighestDark = Color(0xFF303634)

// 状态色（成功 / 警告）
val YuanSuccessLight = Color(0xFF1B7F4D)
val YuanSuccessDark  = Color(0xFF5DD593)
val YuanWarnLight    = Color(0xFFB45309)
val YuanWarnDark     = Color(0xFFFFB68A)

// Brand Gradient 端点
val YuanGradientStart = Color(0xFF0F766E)
val YuanGradientEnd   = Color(0xFF5B5F97)
