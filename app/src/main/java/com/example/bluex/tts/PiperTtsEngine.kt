package com.example.bluex.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class PiperTtsEngine(
    context: Context,
    private val modelConfig: PiperModelConfig = PiperModelConfig(),
    private val style: RobotVoiceStyle = RobotVoiceStyle.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext
    private val initLock = Any()

    @Volatile
    private var synthesizerHandle: Long = 0L

    suspend fun synthesize(text: String): PiperAudioBuffer = withContext(ioDispatcher) {
        val normalizedText = text
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        require(normalizedText.isNotEmpty()) { "El texto del saludo no puede estar vacio" }

        val handle = ensureSynthesizer()
        val nativeResult = PiperNativeBindings.nativeSynthesize(
            handle = handle,
            text = normalizedText,
            speakerId = modelConfig.speakerId,
            lengthScale = style.piperLengthScale,
            noiseScale = style.piperNoiseScale,
            noiseWScale = style.piperNoiseWScale
        ) ?: error("Piper no devolvio audio")

        PiperAudioBuffer(
            pcm16le = floatSamplesToPcm16(nativeResult.samples),
            sampleRateHz = nativeResult.sampleRateHz
        )
    }

    fun release() {
        synchronized(initLock) {
            if (synthesizerHandle != 0L && PiperNativeBindings.isLibraryLoaded) {
                PiperNativeBindings.nativeDestroySynthesizer(synthesizerHandle)
                synthesizerHandle = 0L
            }
        }
    }

    private fun ensureSynthesizer(): Long {
        if (!PiperNativeBindings.isLibraryLoaded) {
            error("La libreria nativa de Piper no esta disponible")
        }

        synchronized(initLock) {
            if (synthesizerHandle != 0L) {
                return synthesizerHandle
            }

            val runtimeRoot = File(appContext.filesDir, "piper_runtime")
            if (!runtimeRoot.exists()) {
                runtimeRoot.mkdirs()
            }

            val modelFile = copyAssetRecursively(
                assetPath = modelConfig.modelAssetPath,
                targetFile = File(runtimeRoot, File(modelConfig.modelAssetPath).name)
            )
            val configFile = copyAssetRecursively(
                assetPath = modelConfig.configAssetPath,
                targetFile = File(runtimeRoot, File(modelConfig.configAssetPath).name)
            )
            val espeakDataDir = File(runtimeRoot, "espeak-ng-data")
            copyAssetRecursively(
                assetPath = modelConfig.espeakDataAssetPath,
                targetFile = espeakDataDir
            )

            check(File(espeakDataDir, "phontab").exists()) {
                "No se encontro phontab dentro de espeak-ng-data"
            }

            synthesizerHandle = PiperNativeBindings.nativeCreateSynthesizer(
                modelPath = modelFile.absolutePath,
                configPath = configFile.absolutePath,
                espeakDataPath = runtimeRoot.absolutePath
            )

            check(synthesizerHandle != 0L) {
                "No se pudo inicializar Piper. Verifica modelo, config y espeak-ng-data."
            }

            Log.i(TAG, "Piper inicializado con modelo ${modelConfig.voiceId}")
            return synthesizerHandle
        }
    }

    private fun copyAssetRecursively(assetPath: String, targetFile: File): File {
        val children = appContext.assets.list(assetPath).orEmpty()
        return if (children.isEmpty()) {
            targetFile.parentFile?.mkdirs()
            if (!targetFile.exists() || targetFile.length() == 0L) {
                appContext.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            targetFile
        } else {
            targetFile.mkdirs()
            children.forEach { childName ->
                copyAssetRecursively(
                    assetPath = "$assetPath/$childName",
                    targetFile = File(targetFile, childName)
                )
            }
            targetFile
        }
    }

    private fun floatSamplesToPcm16(samples: FloatArray): ByteArray {
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val clamped = sample.coerceIn(-1.0f, 1.0f)
            val intSample = (clamped * Short.MAX_VALUE).roundToInt().toShort()
            pcm[index * 2] = (intSample.toInt() and 0xFF).toByte()
            pcm[index * 2 + 1] = ((intSample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private companion object {
        private const val TAG = "PiperTtsEngine"
    }
}
