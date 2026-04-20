package com.example.bluex.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class RobotAudioPlayer(
    private val style: RobotVoiceStyle = RobotVoiceStyle.Default,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var cleanupJob: Job? = null

    suspend fun play(audioBuffer: PiperAudioBuffer) {
        withContext(mainDispatcher) {
            stopInternal()

            val channelMask = if (audioBuffer.channelCount == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                audioBuffer.sampleRateHz,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(audioBuffer.sampleRateHz)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(max(minBufferSize, audioBuffer.pcm16le.size))
                .build()

            require(track.state == AudioTrack.STATE_INITIALIZED) {
                "AudioTrack could not be initialized"
            }

            val bytesWritten = track.write(audioBuffer.pcm16le, 0, audioBuffer.pcm16le.size)
            require(bytesWritten > 0) { "AudioTrack could not receive PCM data" }

            audioTrack = track
            equalizer = buildWallEEqualizer(track.audioSessionId)
            applyPlaybackStyle(track)

            track.play()
            Log.d(TAG, "Reproduciendo saludo Wall-E. frames=${audioBuffer.frameCount}")
            scheduleCleanup(audioBuffer)
        }
    }

    suspend fun stop() {
        withContext(mainDispatcher) {
            stopInternal()
        }
    }

    fun release() {
        cleanupJob?.cancel()
        stopInternal()
    }

    private fun applyPlaybackStyle(track: AudioTrack) {
        runCatching {
            track.playbackParams = PlaybackParams()
                .setPitch(style.playbackPitch)
                .setSpeed(style.playbackSpeed)
        }.onFailure { throwable ->
            Log.w(TAG, "No se pudieron aplicar PlaybackParams roboticos", throwable)
        }
    }

    /**
     * 5-band EQ shaped for Wall-E's "tiny tin speaker" sound.
     *
     * Typical Android Equalizer bands:
     *   Band 0 ≈  60 Hz   → sub-bass     (kill)
     *   Band 1 ≈ 230 Hz   → low-mid      (kill)
     *   Band 2 ≈ 910 Hz   → mid          (slight body boost)
     *   Band 3 ≈ 3.6 kHz  → presence     (MAX boost — metallic peak)
     *   Band 4 ≈ 14 kHz   → air          (moderate boost — sparkle)
     *
     * The target levels come from [RobotVoiceStyle] and are clamped to
     * the device's supported millibel range.
     */
    private fun buildWallEEqualizer(audioSessionId: Int): Equalizer {
        return Equalizer(0, audioSessionId).apply {
            enabled = true

            val minLevel = bandLevelRange[0].toInt()
            val maxLevel = bandLevelRange[1].toInt()

            // Map the style's per-band targets to available hardware bands
            val targetByBand = shortArrayOf(
                style.eqBand0_subBass,
                style.eqBand1_lowMid,
                style.eqBand2_mid,
                style.eqBand3_presence,
                style.eqBand4_air
            )

            for (band in 0 until numberOfBands) {
                val bandIndex = band.toShort()
                val target = if (band < targetByBand.size) {
                    targetByBand[band].toInt()
                } else {
                    // Extra bands (rare): apply air boost
                    style.eqBand4_air.toInt()
                }
                val clamped = target.coerceIn(minLevel, maxLevel).toShort()
                setBandLevel(bandIndex, clamped)

                val freqHz = getCenterFreq(bandIndex) / 1000
                Log.v(TAG, "EQ band $band (${freqHz}Hz): ${clamped}mB")
            }
        }
    }

    private fun scheduleCleanup(audioBuffer: PiperAudioBuffer) {
        cleanupJob?.cancel()
        val estimatedDurationMs = (
            (audioBuffer.frameCount.toDouble() / audioBuffer.sampleRateHz.toDouble()) *
                1000.0 / style.playbackSpeed
            ).toLong() + 500L

        cleanupJob = playbackScope.launch {
            delay(estimatedDurationMs)
            withContext(mainDispatcher) {
                stopInternal()
            }
        }
    }

    private fun stopInternal() {
        cleanupJob?.cancel()
        cleanupJob = null

        equalizer?.let { effect ->
            runCatching { effect.enabled = false }
            runCatching { effect.release() }
        }
        equalizer = null

        audioTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        audioTrack = null
    }

    private companion object {
        private const val TAG = "RobotAudioPlayer"
    }
}
