package com.hangulmate.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Honest state of the Korean TTS voice on this device.
 * Nothing here pretends a voice exists when it does not.
 */
sealed interface TtsState {
    /** Still initialising the engine — do not speak yet. */
    data object Checking : TtsState

    /** Korean voice is installed and ready. */
    data object Ready : TtsState

    /** The engine exists but no Korean voice data is installed. */
    data object MissingVoice : TtsState

    /** The engine failed (initialisation error or other failure). */
    data class Error(val message: String) : TtsState
}

/**
 * Real wrapper over [TextToSpeech] for Korean speech.
 *
 * Korean availability is exposed honestly through [state]: callers must
 * check for [TtsState.Ready] before assuming speech will be heard.
 * If the voice is missing, the UI shows the missing-voice message and
 * points the user at installing Korean voice data — it never fakes output.
 */
class TtsManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow<TtsState>(TtsState.Checking)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var rate: Float = 1.0f
    private var pitch: Float = 1.0f

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                _state.value = TtsState.Error("TextToSpeech initialisation failed (status=$status)")
                return@TextToSpeech
            }
            val result = tts?.setLanguage(Locale.KOREAN)
            _state.value = when (result) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED -> TtsState.MissingVoice
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
                -> TtsState.Ready
                else -> TtsState.MissingVoice
            }
            tts?.setSpeechRate(rate)
            tts?.setPitch(pitch)
        }
    }

    /** True only when the Korean voice is actually ready to speak. */
    fun isKoreanAvailable(): Boolean = _state.value == TtsState.Ready

    /** Speak Korean [text]. No-op unless the voice is ready. */
    fun speak(text: String) {
        if (_state.value != TtsState.Ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hangulmate-${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(this.rate)
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(this.pitch)
    }

    /** Release the engine. Call from the owning component's teardown if used outside the singleton. */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
