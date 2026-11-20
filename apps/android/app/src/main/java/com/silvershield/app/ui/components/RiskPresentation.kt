package com.silvershield.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.silvershield.app.guard.risk.RiskHandleStatus
import com.silvershield.app.guard.risk.RiskLevel as DomainRiskLevel
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 领域风险等级 → 设计系统风险标签等级。
 *
 * 领域枚举（guard.risk.RiskLevel）与展示枚举（ui.components.RiskLevel）职责不同：
 * 前者属于业务模型，后者携带颜色与图标映射，必须集中在一处转换。
 */
fun DomainRiskLevel.toRiskLevel(): RiskLevel = when (this) {
    DomainRiskLevel.High -> RiskLevel.High
    DomainRiskLevel.Medium -> RiskLevel.Medium
    DomainRiskLevel.Low -> RiskLevel.Low
    DomainRiskLevel.Unknown -> RiskLevel.Unknown
}

/** 处理状态展示文案；AI 判断与用户处理状态分开呈现。 */
fun RiskHandleStatus.displayLabel(): String = when (this) {
    RiskHandleStatus.New -> "待查看"
    RiskHandleStatus.NeedsVerification -> "待核实"
    RiskHandleStatus.Handled -> "已处理"
}

/** 记录时间统一展示到分钟，避免老人端出现秒级跳动。 */
fun formatRiskTime(millis: Long): String =
    SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA).format(Date(millis))

/**
 * 模拟数据标签。
 *
 * P06 的示例记录不是真实告警，必须在列表与详情里显著标记，防止误读。
 */
@Composable
fun SampleTag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "模拟数据",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}
