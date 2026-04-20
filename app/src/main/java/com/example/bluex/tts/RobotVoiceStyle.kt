package com.example.bluex.tts

import com.example.bluex.BuildConfig

data class RobotVoiceStyle(

    // ── Piper synthesis controls ────────────────────────────────
    // lengthScale  < 1 = faster phonemes (Wall-E speaks in short bursts)
    // noiseScale     low = less breathiness → cleaner, more "digital" base
    // noiseWScale    low = less waveform randomness → more robotic consistency
    val piperLengthScale: Float = 0.92f,
    val piperNoiseScale: Float = 0.40f,
    val piperNoiseWScale: Float = 0.50f,

    // ── AudioTrack playback params ──────────────────────────────
    // High pitch gives Wall-E's childlike, innocent quality.
    // Slightly slow speed = deliberate, careful robot speech.
    val playbackPitch: Float = 1.72f,
    val playbackSpeed: Float = 0.88f,

    // ── Equalizer (5-band, applied by AudioTrack Equalizer) ────
    // Band 0 (~60 Hz)   : kill all sub-bass
    // Band 1 (~230 Hz)  : kill low-mids → no "chest" warmth
    // Band 2 (~910 Hz)  : slight boost → voice body
    // Band 3 (~3.6 kHz) : MAX boost → metallic resonance peak
    // Band 4 (~14 kHz)  : moderate boost → air / sparkle / "tinniness"
    val eqBand0_subBass: Short = (-1500).toShort(),
    val eqBand1_lowMid: Short = (-1200).toShort(),
    val eqBand2_mid: Short = 300.toShort(),
    val eqBand3_presence: Short = 1500.toShort(),
    val eqBand4_air: Short = 600.toShort(),

    // ── WallEAudioProcessor: Bitcrushing ────────────────────────
    // Effective bit depth.  16 = transparent, 8 = very crunchy.
    // 10 bits gives subtle quantisation artefacts → "old digital speaker".
    val bitcrushBits: Int = 10,

    // ── WallEAudioProcessor: Decimation ─────────────────────────
    // Hold every Nth sample.  2 = mild lo-fi, 3 = noticeable aliasing.
    val decimationFactor: Int = 2,

    // ── WallEAudioProcessor: Resonant filter ────────────────────
    // Biquad bandpass centred at resonanceFreqHz with resonanceQ.
    // Mixed wet/dry at resonanceMix (0 = dry only, 1 = wet only).
    // 3 kHz + Q 5 + 35 % mix → metallic "tin can" character.
    val resonanceFreqHz: Float = 3000f,
    val resonanceQ: Float = 5.0f,
    val resonanceMix: Float = 0.35f,

    // ── WallEAudioProcessor: Pitch micro-wobble ─────────────────
    // Slow LFO on read speed creates ± depth % pitch variation.
    // 3 Hz at ±2.5 % → alive, emotional quality.
    val wobbleLfoHz: Float = 3.0f,
    val wobbleDepth: Float = 0.025f,

    // ── WallEAudioProcessor: Chirps ─────────────────────────────
    // Short frequency-sweep beeps bracketing each phrase.
    // Intro  : ascending  500 → 3000 Hz, 35 ms
    // Outro  : descending 2500 → 600 Hz, 28 ms
    val introChirpStartHz: Float = 500f,
    val introChirpEndHz: Float = 3000f,
    val introChirpDurationMs: Float = 35f,
    val outroChirpStartHz: Float = 2500f,
    val outroChirpEndHz: Float = 600f,
    val outroChirpDurationMs: Float = 28f,
    val chirpAmplitude: Float = 0.25f
) {
    companion object {
        val Default = RobotVoiceStyle()
    }
}

data class PiperModelConfig(
    val modelAssetPath: String = BuildConfig.PIPER_MODEL_ASSET_PATH,
    val configAssetPath: String = BuildConfig.PIPER_MODEL_CONFIG_ASSET_PATH,
    val espeakDataAssetPath: String = BuildConfig.PIPER_ESPEAK_DATA_ASSET_PATH,
    val voiceId: String = "es_ES-carlfm-x_low",
    val speakerId: Int = 0
)

data class PiperAudioBuffer(
    val pcm16le: ByteArray,
    val sampleRateHz: Int,
    val channelCount: Int = 1
) {
    val frameCount: Int
        get() = pcm16le.size / 2 / channelCount
}
