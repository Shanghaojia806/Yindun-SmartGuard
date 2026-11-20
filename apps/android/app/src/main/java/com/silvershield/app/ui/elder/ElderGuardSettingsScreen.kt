package com.silvershield.app.ui.elder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.guard.GuardPreferences
import com.silvershield.app.guard.ReminderVolume
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E13 守护设置。
 *
 * 所有开关与选项都直接写入 DataStore 并立即反映到界面；
 * 设置只影响提醒方式，不影响守护本身是否运行。
 */
@Composable
fun ElderGuardSettingsScreen(
    preferences: GuardPreferences,
    onRiskReminderChange: (Boolean) -> Unit,
    onVoiceReminderChange: (Boolean) -> Unit,
    onVolumeChange: (ReminderVolume) -> Unit,
    onStatusHintChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.pageHorizontal,
            end = Spacing.pageHorizontal,
            top = Spacing.pageVertical,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column {
                Text(
                    text = "页面 E13",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "守护设置",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "设置会立即保存在本机。关闭某一项提醒不会停止守护。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            GuardSection(title = "提醒方式") {
                GuardSwitchRow(
                    title = "风险提醒",
                    description = "识别到可疑内容时，在手机上弹出提醒。",
                    checked = preferences.riskReminderEnabled,
                    onCheckedChange = onRiskReminderChange,
                )
                HorizontalDivider(thickness = Sizes.borderWidth, color = tokens.divider)
                GuardSwitchRow(
                    title = "语音提醒",
                    description = "用语音读出风险提示。关闭后仍保留屏幕文字提醒。",
                    checked = preferences.voiceReminderEnabled,
                    onCheckedChange = onVoiceReminderChange,
                )
                HorizontalDivider(thickness = Sizes.borderWidth, color = tokens.divider)
                Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                    Text(
                        text = "提醒音量",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = "语音提醒使用该偏好，不会直接修改手机的系统媒体音量。",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    ReminderVolume.entries.forEach { volume ->
                        GuardRadioRow(
                            label = volume.label,
                            selected = preferences.reminderVolume == volume,
                            onClick = { onVolumeChange(volume) },
                        )
                    }
                    if (!preferences.voiceReminderEnabled) {
                        Text(
                            text = "语音提醒已关闭，音量偏好暂时不会生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textTertiary,
                        )
                    }
                }
            }
        }

        item {
            GuardSection(title = "检测偏好") {
                Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                    Text(
                        text = "检测方式",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = "守护在手机本地识别短视频画面中的可疑话术与场景，" +
                            "原始画面不会上传。当前为模拟守护，检测偏好只保存设置，" +
                            "不会改变真实识别结果。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "提醒优先级：高风险内容优先提醒，其余风险在您方便时提示。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary,
                    )
                }
            }
        }

        item {
            GuardSection(title = "守护状态提示") {
                GuardSwitchRow(
                    title = "显示守护运行提示",
                    description = "在状态区域显示守护是否正在运行，方便您随时确认。",
                    checked = preferences.statusHintEnabled,
                    onCheckedChange = onStatusHintChange,
                )
            }
        }

        item {
            Text(
                text = "以上设置仅保存在本机，不会上传。当前版本为模拟守护，设置不影响真实采集能力。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
            )
        }
    }
}

@Preview(name = "E13 守护设置", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1000)
@Composable
private fun ElderGuardSettingsScreenPreview() {
    SilverShieldTheme {
        ElderGuardSettingsScreen(
            preferences = GuardPreferences(),
            onRiskReminderChange = {},
            onVoiceReminderChange = {},
            onVolumeChange = {},
            onStatusHintChange = {},
        )
    }
}
