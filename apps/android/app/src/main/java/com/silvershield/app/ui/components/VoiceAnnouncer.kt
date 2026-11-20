package com.silvershield.app.ui.components

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * E06 语音入口：使用系统 TTS 朗读当前提醒。
 *
 * - 不申请任何权限，不联网合成（是否下载语音包由系统语音引擎决定）；
 * - 没有可用引擎或中文语音时回调 [onUnavailable]；
 * - 引擎接受了请求但合成失败（例如语音包缺失）时回调 [onFailed]，
 *   由页面如实提示，而不是让用户以为已经播报；
 * - 页面销毁时立即 shutdown，避免后台持有引擎。
 */
@Composable
fun rememberVoiceAnnouncer(
    onUnavailable: () -> Unit,
    onFailed: () -> Unit,
): (String) -> Unit {
    val context = LocalContext.current
    val engineState = remember { mutableStateOf<TextToSpeech?>(null) }
    val readyState = remember { mutableStateOf(false) }
    val releasedState = remember { mutableStateOf(false) }
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val currentOnFailed by rememberUpdatedState(onFailed)

    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineState.value?.let { tts ->
                    val languageResult = tts.setLanguage(Locale.CHINA)
                    readyState.value = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = Unit

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (!releasedState.value) currentOnFailed()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (!releasedState.value) currentOnFailed()
            }
        })
        engineState.value = engine
        onDispose {
            releasedState.value = true
            readyState.value = false
            engineState.value = null
            runCatching { engine.stop() }
            runCatching { engine.shutdown() }
        }
    }

    val speak: (String) -> Unit = remember {
        { text ->
            val engine = engineState.value
            if (engine == null || !readyState.value) {
                currentOnUnavailable()
            } else {
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
                if (result != TextToSpeech.SUCCESS) currentOnFailed()
            }
        }
    }

    return speak
}

private const val UTTERANCE_ID = "silver-shield-risk-alert"
