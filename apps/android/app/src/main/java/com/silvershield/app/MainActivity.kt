package com.silvershield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.silvershield.app.navigation.SilverShieldNavHost
import com.silvershield.app.ui.theme.SilverShieldTheme

/**
 * 应用唯一入口。
 *
 * P03 起由 [SilverShieldNavHost] 根据本地角色状态决定入口：
 * 首次启动进入引导页（E01），已完成引导则恢复上次选择的老人端或家属端。
 * 当前为本地模拟登录，不包含真实账号认证、屏幕采集与 AI 风险识别。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SilverShieldTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SilverShieldNavHost()
                }
            }
        }
    }
}
