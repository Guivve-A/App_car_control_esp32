package com.example.bluex.tts

import android.os.SystemClock
import android.util.Log

class HandleEsp32MessageUseCase(
    private val greetingManager: GreetingManager,
    private val onGreetingAccepted: (String) -> Unit,
) {
    private var lastAcceptedText: String? = null
    private var lastAcceptedAtMillis: Long = 0L

    fun handleEsp32Message(message: String) {
        Log.d(TAG, "Mensaje ESP32 recibido: $message")

        if (!message.startsWith(TTS_PREFIX)) {
            Log.v(TAG, "Mensaje ignorado porque no inicia con TTS:")
            return
        }

        val normalizedText = normalizeText(message.removePrefix(TTS_PREFIX))
        Log.d(TAG, "Texto TTS normalizado: $normalizedText")

        if (normalizedText.isBlank()) {
            Log.d(TAG, "Mensaje TTS vacio tras normalizacion, se ignora")
            return
        }

        if (isConsecutiveDuplicate(normalizedText)) {
            Log.d(TAG, "Mensaje TTS duplicado consecutivo ignorado: $normalizedText")
            return
        }

        lastAcceptedText = normalizedText
        lastAcceptedAtMillis = SystemClock.elapsedRealtime()
        onGreetingAccepted(normalizedText)
        greetingManager.generateWallEGreeting(normalizedText)
    }

    fun stop() {
        greetingManager.stop()
    }

    fun release() {
        greetingManager.release()
    }

    private fun isConsecutiveDuplicate(normalizedText: String): Boolean {
        val elapsed = SystemClock.elapsedRealtime() - lastAcceptedAtMillis
        return normalizedText == lastAcceptedText && elapsed <= DUPLICATE_SUPPRESSION_WINDOW_MS
    }

    private fun normalizeText(rawText: String): String {
        return rawText
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private companion object {
        private const val TAG = "HandleEsp32Message"
        private const val TTS_PREFIX = "TTS:"
        private const val DUPLICATE_SUPPRESSION_WINDOW_MS = 1500L
    }
}
