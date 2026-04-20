package com.example.bluex.tts

import kotlin.math.*

/**
 * DSP pipeline that transforms clean Piper TTS output into Wall-E's iconic voice.
 *
 * Signal chain:
 *   raw PCM16 → float → bitcrush → decimate → resonant filter →
 *   pitch micro-wobble → soft clip → prepend chirp → append chirp → PCM16
 */
class WallEAudioProcessor(private val style: RobotVoiceStyle = RobotVoiceStyle.Default) {

    fun process(buffer: PiperAudioBuffer): PiperAudioBuffer {
        val sampleRate = buffer.sampleRateHz
        var samples = pcm16ToFloat(buffer.pcm16le)

        // ── 1. Bitcrush ─────────────────────────────────────────
        samples = bitcrush(samples, style.bitcrushBits)

        // ── 2. Decimation (sample-rate reduction feel) ──────────
        samples = decimate(samples, style.decimationFactor)

        // ── 3. Resonant bandpass filter at ~3 kHz ───────────────
        samples = applyResonantFilter(
            samples, sampleRate,
            style.resonanceFreqHz,
            style.resonanceQ,
            style.resonanceMix
        )

        // ── 4. Pitch micro-wobble (LFO on read speed) ──────────
        samples = applyPitchWobble(
            samples, sampleRate,
            style.wobbleLfoHz,
            style.wobbleDepth
        )

        // ── 5. Soft clip to tame peaks ──────────────────────────
        samples = softClip(samples)

        // ── 6. Chirps ───────────────────────────────────────────
        val introChirp = generateChirp(
            sampleRate,
            style.introChirpStartHz,
            style.introChirpEndHz,
            style.introChirpDurationMs,
            style.chirpAmplitude
        )
        val outroChirp = generateChirp(
            sampleRate,
            style.outroChirpStartHz,
            style.outroChirpEndHz,
            style.outroChirpDurationMs,
            style.chirpAmplitude
        )
        val silenceGap = FloatArray((sampleRate * 0.02f).toInt()) // 20 ms silence

        samples = introChirp + silenceGap + samples + silenceGap + outroChirp

        return PiperAudioBuffer(
            pcm16le = floatToPcm16(samples),
            sampleRateHz = sampleRate,
            channelCount = buffer.channelCount
        )
    }

    // ─── Bitcrushing ────────────────────────────────────────────
    // Reduces effective bit depth to introduce subtle quantization noise
    // that gives Wall-E's "old digital speaker" character.
    private fun bitcrush(samples: FloatArray, bits: Int): FloatArray {
        val levels = (1 shl (bits - 1)).toFloat()   // e.g. 10 bits → 512 levels
        return FloatArray(samples.size) { i ->
            val s = samples[i]
            (floor(s * levels) / levels)
        }
    }

    // ─── Decimation ─────────────────────────────────────────────
    // Holds every Nth sample, giving a lo-fi sample-rate-reduced feel
    // without actually changing the buffer's sample rate.
    private fun decimate(samples: FloatArray, factor: Int): FloatArray {
        if (factor <= 1) return samples
        val out = FloatArray(samples.size)
        var held = 0f
        for (i in samples.indices) {
            if (i % factor == 0) held = samples[i]
            out[i] = held
        }
        return out
    }

    // ─── Biquad Resonant Bandpass ───────────────────────────────
    // 2nd-order IIR bandpass tuned to ~3 kHz creates the metallic
    // "small tin speaker" resonance that defines Wall-E's timbre.
    // Mixed with dry signal at `mix` ratio.
    private fun applyResonantFilter(
        samples: FloatArray,
        sampleRate: Int,
        freqHz: Float,
        q: Float,
        mix: Float
    ): FloatArray {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)

        val b0 = alpha
        val b1 = 0.0
        val b2 = -alpha
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cos(w0)
        val a2 = 1.0 - alpha

        // Normalize coefficients
        val nb0 = (b0 / a0).toFloat()
        val nb1 = (b1 / a0).toFloat()
        val nb2 = (b2 / a0).toFloat()
        val na1 = (a1 / a0).toFloat()
        val na2 = (a2 / a0).toFloat()

        val out = FloatArray(samples.size)
        var x1 = 0f; var x2 = 0f
        var y1 = 0f; var y2 = 0f

        val dry = 1f - mix

        for (i in samples.indices) {
            val x0 = samples[i]
            val wet = nb0 * x0 + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2

            out[i] = dry * x0 + mix * wet

            x2 = x1; x1 = x0
            y2 = y1; y1 = wet
        }
        return out
    }

    // ─── Pitch Micro-Wobble ─────────────────────────────────────
    // A slow LFO (2–4 Hz) modulates the playback read position,
    // creating ±2–3 % pitch variation that gives Wall-E his
    // emotional, "alive" quality despite being a robot.
    private fun applyPitchWobble(
        samples: FloatArray,
        sampleRate: Int,
        lfoHz: Float,
        depth: Float          // 0.025 = ±2.5 %
    ): FloatArray {
        if (depth <= 0f) return samples

        val out = FloatArray(samples.size)
        var readPos = 0.0

        for (i in out.indices) {
            val lfoPhase = 2.0 * PI * lfoHz * i / sampleRate
            val speedMod = 1.0 + depth * sin(lfoPhase)

            val intPos = readPos.toInt()
            val frac = (readPos - intPos).toFloat()

            out[i] = when {
                intPos + 1 < samples.size ->
                    samples[intPos] * (1f - frac) + samples[intPos + 1] * frac
                intPos < samples.size -> samples[intPos]
                else -> 0f
            }
            readPos += speedMod
        }
        return out
    }

    // ─── Soft Clipper ───────────────────────────────────────────
    // tanh-based soft saturation keeps peaks warm instead of
    // hard-clipping after cumulative gain from the filter chain.
    private fun softClip(samples: FloatArray): FloatArray {
        return FloatArray(samples.size) { i ->
            tanh(samples[i].toDouble()).toFloat()
        }
    }

    // ─── Chirp / Beep Generator ─────────────────────────────────
    // Synthesizes a short frequency-sweep "chirp" with a smooth
    // amplitude envelope. Mimics Wall-E's characteristic electronic
    // beeps that bracket his speech.
    private fun generateChirp(
        sampleRate: Int,
        startFreqHz: Float,
        endFreqHz: Float,
        durationMs: Float,
        amplitude: Float
    ): FloatArray {
        val numSamples = (sampleRate * durationMs / 1000f).toInt()
        val out = FloatArray(numSamples)

        for (i in out.indices) {
            val t = i.toFloat() / sampleRate                      // time in seconds
            val progress = i.toFloat() / numSamples                // 0→1

            // Linear frequency sweep
            val freq = startFreqHz + (endFreqHz - startFreqHz) * progress

            // Instantaneous phase via integral of frequency sweep
            val phase = 2.0 * PI * (startFreqHz * t +
                    (endFreqHz - startFreqHz) * 0.5 * t * progress)

            // Smooth envelope: quick attack (10%), sustain, quick decay (15%)
            val envelope = when {
                progress < 0.10f -> progress / 0.10f               // fade in
                progress > 0.85f -> (1f - progress) / 0.15f        // fade out
                else -> 1f
            }

            out[i] = (amplitude * envelope * sin(phase)).toFloat()
        }
        return out
    }

    // ─── PCM conversion helpers ─────────────────────────────────

    private fun pcm16ToFloat(pcm: ByteArray): FloatArray {
        val numSamples = pcm.size / 2
        val out = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val sample16 = (hi shl 8) or lo        // signed little-endian
            out[i] = sample16 / 32768f
        }
        return out
    }

    private fun floatToPcm16(samples: FloatArray): ByteArray {
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1f, 1f)
            val int16 = (clamped * Short.MAX_VALUE).roundToInt().toShort()
            pcm[i * 2] = (int16.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((int16.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    companion object {
        private const val PI = Math.PI
    }
}
