package com.douyin.downloader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * 圆圆解析 YuanZai · 动效 tokens
 *
 * - Spring：物理弹性曲线，Tab 切换 / BottomSheet 弹收
 * - Tween：固定时长曲线，页面进入 / Hero 转场 / Skeleton
 * - Easing：M3 Expressive 强调曲线（emphasized）
 */
object YuanMotion {
    /** M3 标准强调曲线：快速启动 + 平滑减速。 */
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    val TabSpring: SpringSpec<Float> = SpringSpec(
        stiffness = 380f,
        dampingRatio = 0.8f,
    )
    val SheetSpring: SpringSpec<Float> = SpringSpec(
        stiffness = 300f,
        dampingRatio = 0.85f,
    )
    val ButtonSpring: SpringSpec<Float> = SpringSpec(
        stiffness = 1200f,
        dampingRatio = 0.6f,
    )

    const val PageEnterMs = 250
    const val SharedElementMs = 450
    const val HeroSuccessMs = 600
    const val ShimmerMs = 1200
    const val ProgressUpdateMs = 100

    fun <T> pageEnter(): TweenSpec<T> = tween(PageEnterMs, easing = Standard)
    fun <T> sharedElement(): TweenSpec<T> = tween(SharedElementMs, easing = Emphasized)
    fun <T> heroSuccess(): TweenSpec<T> = tween(HeroSuccessMs, easing = EmphasizedDecelerate)
    fun <T> shimmer(): TweenSpec<T> = tween(ShimmerMs, easing = Standard)
}
