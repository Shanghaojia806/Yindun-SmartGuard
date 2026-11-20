package com.silvershield.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 银盾智鉴 · 基础色板（Design Tokens）
 *
 * 约束：
 * 1. 本文件是颜色唯一来源，业务组件与页面禁止硬编码十六进制色值。
 * 2. 品牌色、交互色、状态色来自 V1.3 视觉规范，不得擅自改值。
 * 3. 辅助色（背景、文字、边框、禁用、分割线）由既有主色派生，保持色相统一（蓝绿灰体系）。
 */

// ── 品牌与交互 ────────────────────────────────────────────────
/** 品牌主色（V1.3 指定值） */
val BrandPrimary = Color(0xFF174A71)
/** 品牌主色 · 按下 */
val BrandPrimaryPressed = Color(0xFF10364F)
/** 品牌主色 · 悬停 */
val BrandPrimaryHover = Color(0xFF1D5A87)
/** 品牌主色 · 浅色容器 */
val BrandPrimaryLight = Color(0xFFD6E6F2)
/** 品牌主色 · 深色（深色模式主背景、标题） */
val BrandPrimaryDark = Color(0xFF0F3550)
/** 交互主色（V1.3 指定值，链接、次级操作、聚焦态） */
val BrandAccent = Color(0xFF176497)
/** 交互主色 · 按下 */
val BrandAccentPressed = Color(0xFF114C73)
/** 交互主色 · 悬停 */
val BrandAccentHover = Color(0xFF1A76B3)
/** 交互主色 · 浅色底 */
val BrandAccentLight = Color(0xFFE6F1F9)

// ── 语义状态色 ────────────────────────────────────────────────
/** 安全状态（V1.3 指定值） */
val SemanticSafe = Color(0xFF25845E)
/** 安全状态 · 浅色容器 */
val SemanticSafeContainer = Color(0xFFE3F4EB)
/** 警示状态（V1.3 指定值） */
val SemanticWarning = Color(0xFFD8A03A)
/** 警示状态 · 浅色容器 */
val SemanticWarningContainer = Color(0xFFFCF2DD)
/** 危险状态（V1.3 指定值） */
val SemanticDanger = Color(0xFFC83C3C)
/** 危险状态 · 浅色容器 */
val SemanticDangerContainer = Color(0xFFFBE8E8)
/** 中性/未知状态（风险等级未识别时使用） */
val SemanticNeutral = Color(0xFF64798A)
/** 中性状态 · 浅色容器 */
val SemanticNeutralContainer = Color(0xFFEDF2F6)

// ── 中性色 · 浅色模式 ─────────────────────────────────────────
/** 页面背景 */
val SurfaceLight = Color(0xFFF4F7FA)
/** 卡片背景 */
val SurfaceCardLight = Color(0xFFFFFFFF)
/** 次级填充（输入框、弱化区块） */
val SurfaceVariantLight = Color(0xFFF7FAFC)
/** 一级文字 */
val TextPrimaryLight = Color(0xFF11212D)
/** 二级文字 */
val TextSecondaryLight = Color(0xFF51636F)
/** 三级文字（辅助说明、时间戳） */
val TextTertiaryLight = Color(0xFF7F8F9B)
/** 边框 */
val BorderLight = Color(0xFFDBE5EE)
/** 分割线 */
val DividerLight = Color(0xFFEDF2F7)
/** 禁用背景 */
val DisabledContainerLight = Color(0xFFE9EFF4)
/** 禁用文字 */
val DisabledContentLight = Color(0xFF9AAAB5)
/** 悬停底色 */
val HoverOverlayLight = Color(0xFFF0F5FA)
/** 按下底色 */
val PressedOverlayLight = Color(0xFFE4EDF5)

// ── 中性色 · 深色模式 ─────────────────────────────────────────
/** 页面背景 */
val SurfaceDark = Color(0xFF0E1B24)
/** 卡片背景 */
val SurfaceCardDark = Color(0xFF15242F)
/** 次级填充 */
val SurfaceVariantDark = Color(0xFF1B2C39)
/** 一级文字 */
val TextPrimaryDark = Color(0xFFF1F6FA)
/** 二级文字 */
val TextSecondaryDark = Color(0xFFADBECB)
/** 三级文字 */
val TextTertiaryDark = Color(0xFF8397A5)
/** 边框 */
val BorderDark = Color(0xFF2C4150)
/** 分割线 */
val DividerDark = Color(0xFF223442)
/** 禁用背景 */
val DisabledContainerDark = Color(0xFF253743)
/** 禁用文字 */
val DisabledContentDark = Color(0xFF6F8492)
/** 悬停底色 */
val HoverOverlayDark = Color(0xFF1F3442)
/** 按下底色 */
val PressedOverlayDark = Color(0xFF2A4252)
