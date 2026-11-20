// 银盾智鉴 Android 端根构建脚本
// P01 仅建立可编译骨架，不包含采集、推理或业务登录能力。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
