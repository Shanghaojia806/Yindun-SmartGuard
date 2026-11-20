package com.silvershield.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.role.UserRole
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E02 角色选择 / 本地模拟登录页。
 *
 * 【重要】当前为本地模拟登录：仅根据本机选择决定进入老人端还是家属端，
 * 不校验账号密码，不产生真实身份认证与会话，仅用于前端开发与功能测试。
 *
 * @param initialRole 已保存的角色，用于角色切换时预选中
 * @param onBack 返回回调；首次引导进入时为 null（不显示返回）
 * @param onConfirm 确认进入所选角色
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectScreen(
    initialRole: UserRole?,
    onConfirm: (UserRole) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val tokens = SilverShieldTokens.colors
    var selectedRole by rememberSaveable {
        mutableStateOf(initialRole?.storageKey ?: UserRole.Elder.storageKey)
    }
    val selected = UserRole.fromStorageKey(selectedRole) ?: UserRole.Elder

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "选择使用身份",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(Sizes.touchTargetMin),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.pageVertical),
        ) {
            Text(
                text = "请选择您在本机使用的身份",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "同一台手机可以随时切换身份，切换后不会保留上一身份的页面。",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
            )

            Spacer(Modifier.height(Spacing.lg))

            RoleOptionCard(
                title = "我是老人",
                description = "使用守护、查看风险记录，并管理家人共享。",
                icon = Icons.Filled.Person,
                selected = selected == UserRole.Elder,
                onClick = { selectedRole = UserRole.Elder.storageKey },
            )

            Spacer(Modifier.height(Spacing.sm))

            RoleOptionCard(
                title = "我是家属",
                description = "查看家人授权的风险事件，并协助处理。",
                icon = Icons.Filled.Favorite,
                selected = selected == UserRole.Family,
                onClick = { selectedRole = UserRole.Family.storageKey },
            )

            Spacer(Modifier.height(Spacing.lg))

            StatusCard(
                title = "当前为本地模拟登录",
                description = "本页仅根据本机选择进入对应界面，不校验账号密码，也不连接服务器。真实身份认证将在后续版本接入。",
                tone = StatusTone.Warning,
                statusLabel = "开发阶段说明",
            )

            Spacer(Modifier.height(Spacing.xl))

            PrimaryButton(
                text = "以${selected.label}身份进入",
                onClick = { onConfirm(selected) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 角色选项卡片。
 *
 * 选中态同时使用边框、底色与勾选图标表达，不单独依赖颜色。
 */
@Composable
private fun RoleOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else tokens.border
    val containerColor = if (selected) tokens.accentLight else tokens.surfaceCard

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else Sizes.borderWidth, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.statusCardIconContainer)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else tokens.surfaceSubtle,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else tokens.textSecondary,
                    modifier = Modifier.size(Sizes.iconMd),
                )
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                )
            }

            if (selected) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconMd),
                )
            }
        }
    }
}

@Preview(name = "E02 角色选择", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun RoleSelectScreenPreview() {
    SilverShieldTheme {
        RoleSelectScreen(initialRole = UserRole.Elder, onConfirm = {}, onBack = {})
    }
}

@Preview(name = "E02 角色选择 · 字体放大 1.8x", fontScale = 1.8f, showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun RoleSelectLargeFontPreview() {
    SilverShieldTheme {
        RoleSelectScreen(initialRole = UserRole.Family, onConfirm = {}, onBack = {})
    }
}
