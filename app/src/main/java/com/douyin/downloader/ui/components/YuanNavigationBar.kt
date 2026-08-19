package com.douyin.downloader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.douyin.downloader.ui.theme.YuanMotion

/**
 * 圆圆解析 YuanZai · 底部导航栏
 *
 * 5 Tab M3 Expressive：active pill indicator、徽标、Spring 切换动画。
 */
data class YuanNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
)

@Composable
fun YuanNavigationBar(
    currentKey: String,
    onSelect: (YuanNavItem) -> Unit,
    items: List<YuanNavItem>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(80.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                YuanNavButton(
                    item = item,
                    selected = item.key == currentKey,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun YuanNavButton(
    item: YuanNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant
    val tint by animateColorAsState(targetColor, label = "nav-tint")
    val targetPill = if (selected) 1f else 0f
    val pill by animateFloatAsState(
        targetValue = targetPill,
        animationSpec = YuanMotion.TabSpring,
        label = "nav-pill",
    )
    val pillColor = MaterialTheme.colorScheme.secondaryContainer
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = YuanMotion.TabSpring,
        label = "nav-scale",
    )

    val containerColor = lerpColor(Color.Transparent, pillColor, pill)

    Box(
        modifier = modifier
            .height(64.dp)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            BadgedBox(badge = {
                if (item.badgeCount > 0) {
                    Badge { Text(item.badgeCount.toString()) }
                }
            }) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale),
                )
            }
            Spacer(Modifier.size(2.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red   = start.red   + (end.red   - start.red)   * f,
        green = start.green + (end.green - start.green) * f,
        blue  = start.blue  + (end.blue  - start.blue)  * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}