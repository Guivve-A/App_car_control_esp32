package com.example.bluex.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class FallbackTextToSpeechEngine(
    context: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val readyState = CompletableDeferred<Boolean>()
    private var textToSpeech: TextToSpeech? = TextToSpeech(appContext, this)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            runCatching {
                configureVoice()
                readyState.complete(true)
                Log.i(TAG, "Fallback TextToSpeech listo")
            }.onFailure { throwable ->
                readyState.complete(false)
                Log.e(TAG, "No se pudo configurar el fallback TextToSpeech", throwable)
            }
        } else {
            readyState.complete(false)
            Log.e(TAG, "Fallo la inicializacion del fallback TextToSpeech: $status")
        }
    }

    suspend fun speak(text: String) {
        if (!awaitReady()) {
            Log.e(TAG, "Fallback TextToSpeech no disponible para pronunciar texto")
            return
        }

        withContext(mainDispatcher) {
            textToSpeech?.stop()
            textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "fallback_robot_${System.currentTimeMillis()}"
            )
            Log.d(TAG, "Fallback nativo pronunciando: $text")
        }
    }

    suspend fun stop() {
        withContext(mainDispatcher) {
            textToSpeech?.stop()
        }
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private suspend fun awaitReady(): Boolean {
        return withTimeoutOrNull(3500L) { readyState.await() } ?: false
    }

    private fun configureVoice() {
        val engine = textToSpeech ?: error("TextToSpeech engine is null")

        val locale = listOf(
            Locale.forLanguageTag("es-ES"),
            Locale.forLanguageTag("es-US"),
            Locale.forLanguageTag("es")
        ).firstOrNull { candidate ->
            val result = engine.setLanguage(candidate)
            result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        } ?: Locale.forLanguageTag("es")

        engine.setLanguage(locale)
        selectBestSpanishVoice(engine)
        engine.setPitch(1.55f)
        engine.setSpeechRate(0.93f)
    }

    private fun selectBestSpanishVoice(engine: TextToSpeech) {
        val preferredVoice = engine.voices
            ?.asSequence()
            ?.filter { voice -> voice.locale.language == "es" }
            ?.sortedWith(
                compareBy<Voice> { it.isNetworkConnectionRequired }
                    .thenBy { it.features.contains("notInstalled") }
                    .thenBy { !it.name.contains("female", ignoreCase = true) }
            )
            ?.firstOrNull()

        if (preferredVoice != null) {
            engine.voice = preferredVoice
            Log.d(TAG, "Fallback voz seleccionada: ${preferredVoice.name}")
        }
    }

    private companion object {
        private const val TAG = "FallbackTtsEngine"
    }
}
