package com.silvershield.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.navigation.ElderTab
import com.silvershield.app.navigation.FamilyTab
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens

/**
 * 底部导航条目描述。
 *
 * 导航栏只依赖本数据类，不关心是老人端还是家属端，
 * 因此两端可以共享同一套导航栏实现而保持路由独立。
 */
@Immutable
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** 读屏补充说明，避免只朗读两个字的标签 */
    val contentDescription: String,
)

/** 老人端一级入口 → 导航条目 */
fun ElderTab.toNavItem(): BottomNavItem = BottomNavItem(
    route = route,
    label = label,
    icon = when (this) {
        ElderTab.Guard -> Icons.Filled.Lock
        ElderTab.Records -> Icons.AutoMirrored.Filled.List
        ElderTab.Profile -> Icons.Filled.Person
    },
    contentDescription = "$label，${pageCode}${title}",
)

/** 家属端一级入口 → 导航条目 */
fun FamilyTab.toNavItem(): BottomNavItem = BottomNavItem(
    route = route,
    label = label,
    icon = when (this) {
        FamilyTab.Home -> Icons.Filled.Home
        FamilyTab.Risks -> Icons.Filled.Warning
        FamilyTab.Cooperation -> Icons.Filled.Share
        FamilyTab.Profile -> Icons.Filled.Person
    },
    contentDescription = "$label，${pageCode}${title}",
)

/**
 * 角色端通用底部导航栏。
 *
 * - 选中态由调用方根据当前路由判定，避免导航组件内部猜测路由；
 * - 文字标签始终显示（不使用纯图标导航），符合适老化要求；
 * - 未选中项同样使用较深的中性色，保证对比度。
 */
@Composable
fun RoleBottomBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onSelect: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = tokens.accentLight,
                    unselectedIconColor = tokens.textSecondary,
                    unselectedTextColor = tokens.textSecondary,
                ),
            )
        }
    }
}

@Preview(name = "老人端底部导航", showBackground = true, widthDp = 360)
@Composable
private fun ElderBottomBarPreview() {
    SilverShieldTheme {
        RoleBottomBar(
            items = ElderTab.entries.map { it.toNavItem() },
            selectedRoute = ElderTab.START.route,
            onSelect = {},
        )
    }
}

@Preview(name = "家属端底部导航", showBackground = true, widthDp = 360)
@Composable
private fun FamilyBottomBarPreview() {
    SilverShieldTheme {
        RoleBottomBar(
            items = FamilyTab.entries.map { it.toNavItem() },
            selectedRoute = FamilyTab.Risks.route,
            onSelect = {},
        )
    }
}
