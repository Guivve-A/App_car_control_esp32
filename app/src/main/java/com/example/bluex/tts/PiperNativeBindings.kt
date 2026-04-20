package com.example.bluex.tts

import android.util.Log

data class PiperNativeSynthesisResult(
    val samples: FloatArray,
    val sampleRateHz: Int
)

object PiperNativeBindings {
    private const val TAG = "PiperNativeBindings"

    val isLibraryLoaded: Boolean by lazy {
        runCatching {
            System.loadLibrary("bluex_piper_jni")
            true
        }.getOrElse { throwable ->
            Log.e(TAG, "No se pudo cargar bluex_piper_jni", throwable)
            false
        }
    }

    external fun nativeCreateSynthesizer(
        modelPath: String,
        configPath: String,
        espeakDataPath: String
    ): Long

    external fun nativeDestroySynthesizer(handle: Long)

    external fun nativeSynthesize(
        handle: Long,
        text: String,
        speakerId: Int,
        lengthScale: Float,
        noiseScale: Float,
        noiseWScale: Float
    ): PiperNativeSynthesisResult?
}
