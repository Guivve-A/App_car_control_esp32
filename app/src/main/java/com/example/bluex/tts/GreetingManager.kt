package com.example.bluex.tts

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GreetingManager(
    private val scope: CoroutineScope,
    private val piperTtsEngine: PiperTtsEngine,
    private val robotAudioPlayer: RobotAudioPlayer,
    private val wallEAudioProcessor: WallEAudioProcessor = WallEAudioProcessor(),
    private val fallbackTextToSpeechEngine: FallbackTextToSpeechEngine? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var activeJob: Job? = null

    fun generateWallEGreeting(input: String) {
        val normalizedInput = input
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalizedInput.isBlank()) {
            Log.w(TAG, "Se ignoro un saludo vacio")
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                robotAudioPlayer.stop()
                fallbackTextToSpeechEngine?.stop()

                val rawBuffer = withContext(ioDispatcher) {
                    piperTtsEngine.synthesize(normalizedInput)
                }

                // Apply Wall-E DSP chain: bitcrush → decimate → resonance →
                // pitch wobble → soft clip → chirps
                val processedBuffer = withContext(ioDispatcher) {
                    wallEAudioProcessor.process(rawBuffer)
                }

                robotAudioPlayer.play(processedBuffer)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                Log.e(TAG, "Piper fallo. Activando fallback nativo.", throwable)
                fallbackTextToSpeechEngine?.speak(normalizedInput)
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        scope.launch {
            robotAudioPlayer.stop()
            fallbackTextToSpeechEngine?.stop()
        }
    }

    fun release() {
        activeJob?.cancel()
        robotAudioPlayer.release()
        piperTtsEngine.release()
        fallbackTextToSpeechEngine?.shutdown()
    }

    private companion object {
        private const val TAG = "GreetingManager"
    }
}
