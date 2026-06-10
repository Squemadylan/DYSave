package com.douyin.downloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 圆圆解析 YuanZai · Material 3 Expressive 主题入口
 *
 * 色彩路径优先级：
 *   1. Android 12+ (SDK ≥ 31) 且调用方启用 dynamicColor → 跟随系统壁纸取色
 *   2. 否则 → 品牌兜底（青 → 靛 渐变系）
 *
 * 字体 / 形状 / 动效 token 全部来自 ui.theme.* 命名空间。
 */

private val YuanLightColorScheme = lightColorScheme(
    primary = YuanPrimaryLight,
    onPrimary = YuanOnPrimaryLight,
    primaryContainer = YuanPrimaryContainerLight,
    onPrimaryContainer = YuanOnPrimaryContainerLight,

    secondary = YuanSecondaryLight,
    onSecondary = YuanOnSecondaryLight,
    secondaryContainer = YuanSecondaryContainerLight,
    onSecondaryContainer = YuanOnSecondaryContainerLight,

    tertiary = YuanTertiaryLight,
    onTertiary = YuanOnTertiaryLight,
    tertiaryContainer = YuanTertiaryContainerLight,
    onTertiaryContainer = YuanOnTertiaryContainerLight,

    error = YuanErrorLight,
    onError = YuanOnErrorLight,
    errorContainer = YuanErrorContainerLight,
    onErrorContainer = YuanOnErrorContainerLight,

    background = YuanBackgroundLight,
    onBackground = YuanOnBackgroundLight,
    surface = YuanSurfaceLight,
    onSurface = YuanOnSurfaceLight,

    surfaceVariant = YuanSurfaceVariantLight,
    onSurfaceVariant = YuanOnSurfaceVariantLight,
    surfaceTint = YuanSurfaceTintLight,

    inverseSurface = YuanInverseSurfaceLight,
    inverseOnSurface = YuanInverseOnSurfaceLight,
    inversePrimary = YuanInversePrimaryLight,

    outline = YuanOutlineLight,
    outlineVariant = YuanOutlineVariantLight,
    scrim = YuanScrimLight,

    surfaceBright = YuanSurfaceBrightLight,
    surfaceDim = YuanSurfaceDimLight,
    surfaceContainerLowest = YuanSurfaceContainerLowestLight,
    surfaceContainerLow = YuanSurfaceContainerLowLight,
    surfaceContainer = YuanSurfaceContainerLight,
    surfaceContainerHigh = YuanSurfaceContainerHighLight,
    surfaceContainerHighest = YuanSurfaceContainerHighestLight,
)

private val YuanDarkColorScheme = darkColorScheme(
    primary = YuanPrimaryDark,
    onPrimary = YuanOnPrimaryDark,
    primaryContainer = YuanPrimaryContainerDark,
    onPrimaryContainer = YuanOnPrimaryContainerDark,

    secondary = YuanSecondaryDark,
    onSecondary = YuanOnSecondaryDark,
    secondaryContainer = YuanSecondaryContainerDark,
    onSecondaryContainer = YuanOnSecondaryContainerDark,

    tertiary = YuanTertiaryDark,
    onTertiary = YuanOnTertiaryDark,
    tertiaryContainer = YuanTertiaryContainerDark,
    onTertiaryContainer = YuanOnTertiaryContainerDark,

    error = YuanErrorDark,
    onError = YuanOnErrorDark,
    errorContainer = YuanErrorContainerDark,
    onErrorContainer = YuanOnErrorContainerDark,

    background = YuanBackgroundDark,
    onBackground = YuanOnBackgroundDark,
    surface = YuanSurfaceDark,
    onSurface = YuanOnSurfaceDark,

    surfaceVariant = YuanSurfaceVariantDark,
    onSurfaceVariant = YuanOnSurfaceVariantDark,
    surfaceTint = YuanSurfaceTintDark,

    inverseSurface = YuanInverseSurfaceDark,
    inverseOnSurface = YuanInverseOnSurfaceDark,
    inversePrimary = YuanInversePrimaryDark,

    outline = YuanOutlineDark,
    outlineVariant = YuanOutlineVariantDark,
    scrim = YuanScrimDark,

    surfaceBright = YuanSurfaceBrightDark,
    surfaceDim = YuanSurfaceDimDark,
    surfaceContainerLowest = YuanSurfaceContainerLowestDark,
    surfaceContainerLow = YuanSurfaceContainerLowDark,
    surfaceContainer = YuanSurfaceContainerDark,
    surfaceContainerHigh = YuanSurfaceContainerHighDark,
    surfaceContainerHighest = YuanSurfaceContainerHighestDark,
)

@Composable
fun DouyinDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> YuanDarkColorScheme
        else -> YuanLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YuanTypography,
        shapes = YuanShapes,
        content = content,
    )
}
