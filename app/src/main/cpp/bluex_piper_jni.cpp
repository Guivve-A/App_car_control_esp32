#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>

#include "piper.h"

namespace {
constexpr const char *TAG = "BluexPiperJni";

void log_error(const char *message) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", message);
}

std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (!value) {
        return {};
    }

    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) {
        return {};
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_bluex_tts_PiperNativeBindings_nativeCreateSynthesizer(
    JNIEnv *env,
    jobject /* this */,
    jstring modelPath,
    jstring configPath,
    jstring espeakDataPath) {
    const std::string model = jstring_to_string(env, modelPath);
    const std::string config = jstring_to_string(env, configPath);
    const std::string espeak = jstring_to_string(env, espeakDataPath);

    piper_synthesizer *synth =
        piper_create(model.c_str(), config.c_str(), espeak.c_str());
    if (!synth) {
        log_error("piper_create returned null");
        return 0;
    }

    return reinterpret_cast<jlong>(synth);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_bluex_tts_PiperNativeBindings_nativeDestroySynthesizer(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong handle) {
    auto *synth = reinterpret_cast<piper_synthesizer *>(handle);
    if (!synth) {
        return;
    }

    piper_free(synth);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_bluex_tts_PiperNativeBindings_nativeSynthesize(
    JNIEnv *env,
    jobject /* this */,
    jlong handle,
    jstring text,
    jint speakerId,
    jfloat lengthScale,
    jfloat noiseScale,
    jfloat noiseWScale) {
    auto *synth = reinterpret_cast<piper_synthesizer *>(handle);
    if (!synth) {
        log_error("nativeSynthesize received a null synthesizer");
        return nullptr;
    }

    std::string input = jstring_to_string(env, text);
    if (input.empty()) {
        log_error("nativeSynthesize received an empty input");
        return nullptr;
    }

    piper_synthesize_options options = piper_default_synthesize_options(synth);
    options.speaker_id = static_cast<int>(speakerId);
    options.length_scale = static_cast<float>(lengthScale);
    options.noise_scale = static_cast<float>(noiseScale);
    options.noise_w_scale = static_cast<float>(noiseWScale);

    if (piper_synthesize_start(synth, input.c_str(), &options) != PIPER_OK) {
        log_error("piper_synthesize_start failed");
        return nullptr;
    }

    std::vector<float> merged_samples;
    int sample_rate = 0;

    while (true) {
        piper_audio_chunk chunk{};
        int result = piper_synthesize_next(synth, &chunk);
        if (result == PIPER_ERR_GENERIC) {
            log_error("piper_synthesize_next failed");
            return nullptr;
        }

        if (chunk.samples && chunk.num_samples > 0) {
            if (sample_rate <= 0) {
                sample_rate = chunk.sample_rate;
            }

            merged_samples.insert(
                merged_samples.end(),
                chunk.samples,
                chunk.samples + chunk.num_samples
            );
        }

        if (result == PIPER_DONE || chunk.is_last) {
            break;
        }
    }

    if (merged_samples.empty() || sample_rate <= 0) {
        log_error("Piper produced no audio samples");
        return nullptr;
    }

    jfloatArray samples_array = env->NewFloatArray(static_cast<jsize>(merged_samples.size()));
    env->SetFloatArrayRegion(
        samples_array,
        0,
        static_cast<jsize>(merged_samples.size()),
        merged_samples.data()
    );

    jclass result_class = env->FindClass("com/example/bluex/tts/PiperNativeSynthesisResult");
    if (!result_class) {
        log_error("Could not find PiperNativeSynthesisResult");
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(result_class, "<init>", "([FI)V");
    if (!ctor) {
        log_error("Could not find PiperNativeSynthesisResult constructor");
        return nullptr;
    }

    return env->NewObject(result_class, ctor, samples_array, sample_rate);
}
