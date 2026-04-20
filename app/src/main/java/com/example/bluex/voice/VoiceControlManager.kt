package com.example.bluex.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceControlManager(private val context: Context) {

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText = _recognizedText.asStateFlow()

    private val _command = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val command = _command.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isAvailable = false

    private val commandMap = mapOf(
        // Movimiento basico
        "adelante" to "F",
        "avanza" to "F",
        "avanzar" to "F",
        "hacia adelante" to "F",
        "atras" to "B",
        "atrás" to "B",
        "retrocede" to "B",
        "retroceder" to "B",
        "hacia atras" to "B",
        "hacia atrás" to "B",
        "izquierda" to "L",
        "gira izquierda" to "L",
        "gira a la izquierda" to "L",
        "derecha" to "R",
        "gira derecha" to "R",
        "gira a la derecha" to "R",
        "para" to "S",
        "parar" to "S",
        "stop" to "S",
        "detente" to "S",
        "alto" to "S",
        "frena" to "S",
        // Movimientos especiales / novedosos
        "explorar" to "X",
        "explora" to "X",
        "modo explorar" to "X",
        "bailar" to "D",
        "baila" to "D",
        "modo baile" to "D",
        "danza" to "D",
        "vigilar" to "V",
        "vigila" to "V",
        "modo vigilar" to "V",
        "escanear" to "V",
        "susto" to "P",
        "asustar" to "P",
        "modo susto" to "P",
        "broma" to "P",
        // Ojos
        "enciende ojos" to "ON",
        "ojos encendidos" to "ON",
        "apaga ojos" to "OFF",
        "ojos apagados" to "OFF",
    )

    fun initialize() {
        isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isAvailable) {
            Log.w(TAG, "Reconocimiento de voz no disponible en este dispositivo")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createListener())
    }

    fun startListening() {
        if (!isAvailable || speechRecognizer == null) {
            Log.w(TAG, "SpeechRecognizer no disponible")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar escucha", e)
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _isListening.value = false
    }

    fun release() {
        stopListening()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun matchCommand(text: String): String? {
        val normalized = text.lowercase().trim()
        // Exact match first
        commandMap[normalized]?.let { return it }
        // Partial match: check if the spoken text contains a command keyword
        for ((key, cmd) in commandMap.entries.sortedByDescending { it.key.length }) {
            if (normalized.contains(key)) {
                return cmd
            }
        }
        return null
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Listo para escuchar")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Inicio de habla detectado")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Fin de habla")
            _isListening.value = false
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                SpeechRecognizer.ERROR_NO_MATCH -> "No se reconocio el comando"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout de habla"
                else -> "Error desconocido ($error)"
            }
            Log.w(TAG, "Error de reconocimiento: $errorMsg")
            _isListening.value = false
            _recognizedText.value = errorMsg
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val bestMatch = matches[0]
                _recognizedText.value = bestMatch
                Log.d(TAG, "Resultado: $bestMatch")

                // Try each result until we find a command match
                for (match in matches) {
                    val cmd = matchCommand(match)
                    if (cmd != null) {
                        Log.i(TAG, "Comando detectado: '$match' -> $cmd")
                        _command.tryEmit(cmd)
                        return
                    }
                }
                Log.d(TAG, "Ninguna coincidencia de comando para: $matches")
                _recognizedText.value = "\"$bestMatch\" - comando no reconocido"
            }
            _isListening.value = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!partial.isNullOrEmpty()) {
                _recognizedText.value = partial[0]
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "VoiceControlManager"
    }
}
