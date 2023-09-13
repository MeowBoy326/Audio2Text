#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <cstdlib>
#include <sys/sysinfo.h>
#include <cstring>
#include "libwhisper/ggml.h"
#include "libwhisper/whisper.h"

#define UNUSED(x) (void)(x)
#define TAG "JNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)

static inline int min(int a, int b) {
    return (a < b) ? a : b;
}

static inline int max(int a, int b) {
    return (a > b) ? a : b;
}

struct input_stream_context {
    size_t offset;
    JNIEnv * env;
    jobject thiz;
    jobject input_stream;

    jmethodID mid_available;
    jmethodID mid_read;
};

// Global variable to hold the callback object
jobject g_progressCallback = nullptr;
jobject g_segmentCallback = nullptr;
jobject g_inferenceStoppedCallback = nullptr;
//bool g_isStopped = false;

size_t inputStreamRead(void * ctx, void * output, size_t read_size) {
    auto* is = (struct input_stream_context*)ctx;

    jint avail_size = is->env->CallIntMethod(is->input_stream, is->mid_available);
    jint size_to_copy = read_size < avail_size ? (jint)read_size : avail_size;

    jbyteArray byte_array = is->env->NewByteArray(size_to_copy);

    jint n_read = is->env->CallIntMethod(is->input_stream, is->mid_read, byte_array, 0, size_to_copy);

    if (size_to_copy != read_size || size_to_copy != n_read) {
        LOGI("Insufficient Read: Req=%zu, ToCopy=%d, Available=%d", read_size, size_to_copy, n_read);
    }

    jbyte* byte_array_elements = is->env->GetByteArrayElements(byte_array, nullptr);
    memcpy(output, byte_array_elements, size_to_copy);
    is->env->ReleaseByteArrayElements(byte_array, byte_array_elements, JNI_ABORT);

    is->env->DeleteLocalRef(byte_array);

    is->offset += size_to_copy;

    return size_to_copy;
}
bool inputStreamEof(void * ctx) {
    auto* is = (struct input_stream_context*)ctx;

    jint result = is->env->CallIntMethod(is->input_stream, is->mid_available);
    return result <= 0;
}
void inputStreamClose(void * ctx) {

}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_setStopped(JNIEnv *env, jobject thiz, jlong ptr) {
    auto *context = (struct whisper_context *) ptr;
    __android_log_print(ANDROID_LOG_INFO, "Whisper", "setStopped definitive");
    set_is_stopped(context);
}

void reportProgress(JNIEnv *env, int progress) {
    if (g_progressCallback != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "send to progress : %d", progress);
        jclass callbackClass = env->GetObjectClass(g_progressCallback);
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "created callback Class");
        jmethodID methodId = env->GetMethodID(callbackClass, "onTranscriptionProgressNonSuspend", "(I)V");
        env->CallVoidMethod(g_progressCallback, methodId, progress);
    }
}

void reportSegment(JNIEnv *env, jstring segment) {
    if (g_segmentCallback != nullptr) {
        const char *segmentStr = env->GetStringUTFChars(segment, nullptr);
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "send to segment: %s", segmentStr);
        jclass callbackClass = env->GetObjectClass(g_segmentCallback);
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "created callback Class for segment");
        jmethodID methodId = env->GetMethodID(callbackClass, "onNewSegment",
                                                 "(Ljava/lang/String;)V");
        env->CallVoidMethod(g_segmentCallback, methodId, segment);
    }
}

void reportStop(JNIEnv *env) {
    if (g_inferenceStoppedCallback != nullptr) {
        jclass callbackClass = env->GetObjectClass(g_inferenceStoppedCallback);
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "created callback Class for stop inference");
        jmethodID methodId = env->GetMethodID(callbackClass, "onInferenceStopped", "()V");
        if (methodId != nullptr) {
            env->CallVoidMethod(g_inferenceStoppedCallback, methodId);
        }
    }
}

/*JNIEXPORT void JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_sendProgressBroadcast(JNIEnv *env, jclass clazz, jobject context, jint progress) {
    jclass intentClass = (*env)->FindClass(env, "android/content/Intent");
    jmethodID intentConstructor = (*env)->GetMethodID(env, intentClass, "<init>", "(Ljava/lang/String;)V");
    jstring jaction = (*env)->NewStringUTF(env, "com.example.audio2text.UPDATE_PROGRESS");
    jobject intent = (*env)->NewObject(env, intentClass, intentConstructor, jaction); // Création de l'objet Intent

    jmethodID putExtraMethod = (*env)->GetMethodID(env, intentClass, "putExtra", "(Ljava/lang/String;I)Landroid/content/Intent;");
    jstring jprogressKey = (*env)->NewStringUTF(env, "progress");
    (*env)->CallObjectMethod(env, intent, putExtraMethod, jprogressKey, progress); // Appel de putExtra

    // Envoyez la diffusion
    jclass contextClass = (*env)->FindClass(env, "android/content/Context");
    jmethodID sendBroadcastMethod = (*env)->GetMethodID(env, contextClass, "sendBroadcast", "(Landroid/content/Intent;)V");
    (*env)->CallVoidMethod(env, context, sendBroadcastMethod, intent); // Envoi de la diffusion
}*/

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_setTranscriptionSegmentListener(JNIEnv *env, jobject thiz, jobject listener) {
    // Conserver une référence globale à l'objet de rappel
    __android_log_print(ANDROID_LOG_INFO, "Whisper", "setSegmentListener");
    g_segmentCallback = env->NewGlobalRef(listener);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_setTranscriptionProgressListener(JNIEnv *env, jobject thiz, jobject listener) {
    // Conserver une référence globale à l'objet de rappel
    __android_log_print(ANDROID_LOG_INFO, "Whisper", "setProgressListener");
    g_progressCallback = env->NewGlobalRef(listener);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_setInferenceStoppedListener(JNIEnv *env, jobject thiz, jobject listener) {
    // Conserver une référence globale à l'objet de rappel
    __android_log_print(ANDROID_LOG_INFO, "Whisper", "setInferenceStoppedListener");
    g_inferenceStoppedCallback = env->NewGlobalRef(listener);
}

void myProgressCallback(struct whisper_context * ctx, struct whisper_state * state, int progress, void * user_data) {
    auto *env = (JNIEnv *)user_data;
    __android_log_print(ANDROID_LOG_INFO, "whisper", "progress : %d", progress);
    // Report the progress to Kotlin (using the reportProgress function from previous steps)
    reportProgress(env, progress);
}

void myAbortingCallback(struct whisper_context * ctx, struct whisper_state * state, void * user_data) {
    __android_log_print(ANDROID_LOG_INFO, "whisper", "Call to abort callback");
    // Report the progress to Kotlin (using the reportProgress function from previous steps)
    __android_log_print(ANDROID_LOG_INFO, "Whisper", "Call to cancellation during inference");
    auto *env = (JNIEnv *)user_data;
    reportStop(env);
}

void mySegmentCallback(struct whisper_context * ctx, struct whisper_state * state, int n_new, void * user_data) {
    auto *env = (JNIEnv *)user_data;
    __android_log_print(ANDROID_LOG_INFO, "whisper", "progress : %d", n_new);
    // Get the last segment
    whisper_segment* segment = get_last_segment(state);

    jstring jsegment = env->NewStringUTF(segment->text.c_str());
    // Report the progress to Kotlin (using the reportProgress function from previous steps)
    reportSegment(env, jsegment);
    delete segment;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_initContextFromInputStream(
        JNIEnv *env, jobject thiz, jobject input_stream) {
    UNUSED(thiz);

    struct whisper_context *context = nullptr;
    struct whisper_model_loader loader = {};
    struct input_stream_context inp_ctx = {};

    inp_ctx.offset = 0;
    inp_ctx.env = env;
    inp_ctx.thiz = thiz;
    inp_ctx.input_stream = input_stream;

    jclass cls = env->GetObjectClass(input_stream);
    inp_ctx.mid_available = env->GetMethodID(cls, "available", "()I");
    inp_ctx.mid_read = env->GetMethodID(cls, "read", "([BII)I");

    loader.context = &inp_ctx;
    loader.read = inputStreamRead;
    loader.eof = inputStreamEof;
    loader.close = inputStreamClose;

    loader.eof(loader.context);

    context = whisper_init(&loader);
    return (jlong) context;
}

static size_t asset_read(void *ctx, void *output, size_t read_size) {
    return AAsset_read((AAsset *) ctx, output, read_size);
}

static bool asset_is_eof(void *ctx) {
    return AAsset_getRemainingLength64((AAsset *) ctx) <= 0;
}

static void asset_close(void *ctx) {
    AAsset_close((AAsset *) ctx);
}

static struct whisper_context *whisper_init_from_asset(
        JNIEnv *env,
        jobject assetManager,
        const char *asset_path
) {
    LOGI("Loading model from asset '%s'", asset_path);
    AAssetManager *asset_manager = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = AAssetManager_open(asset_manager, asset_path, AASSET_MODE_STREAMING);
    if (!asset) {
        LOGW("Failed to open '%s'", asset_path);
        return nullptr;
    }

    whisper_model_loader loader = {
            .context = asset,
            .read = &asset_read,
            .eof = &asset_is_eof,
            .close = &asset_close
    };

    struct whisper_context *context = whisper_init(&loader);

    if (context) {
        LOGI("Successfully initialized the context");
    } else {
        LOGW("Failed to initialize the context");
    }

    return context;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_initContextFromAsset(
        JNIEnv *env, jobject thiz, jobject assetManager, jstring asset_path_str) {
    UNUSED(thiz);

    const char *asset_path_chars = env->GetStringUTFChars(asset_path_str, nullptr);

    // Log pour vérifier le chemin de l'asset
    __android_log_print(ANDROID_LOG_INFO, "WhisperLib", "Asset Path: %s", asset_path_chars);

    struct whisper_context *context = nullptr;
    context = whisper_init_from_asset(env, assetManager, asset_path_chars);

    // Vous pouvez également ajouter d'autres logs ici pour vérifier d'autres valeurs
    __android_log_print(ANDROID_LOG_INFO, "WhisperLib", "Model loaded");
    env->ReleaseStringUTFChars(asset_path_str, asset_path_chars);
    return (jlong) context;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    struct whisper_context *context = nullptr;
    const char *model_path_chars = env->GetStringUTFChars(model_path_str, nullptr);
    context = whisper_init_from_file(model_path_chars);
    env->ReleaseStringUTFChars(model_path_str, model_path_chars);
    return (jlong) context;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto *context = (struct whisper_context *) context_ptr;
    whisper_free(context);
    if (g_progressCallback != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "entered free g_progressCallback");
        env->DeleteGlobalRef(g_progressCallback);
        g_progressCallback = nullptr;
    }
    if (g_segmentCallback != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "entered free g_SegmentCallback");
        env->DeleteGlobalRef(g_segmentCallback);
        g_segmentCallback = nullptr;
    }
    if (g_inferenceStoppedCallback != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "entered free g_inferenceStoppedCallback");
        env->DeleteGlobalRef(g_inferenceStoppedCallback);
        g_inferenceStoppedCallback = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray audio_data, jstring language_code, jstring language_to_ignore,
        jboolean translate, jboolean speed, jstring initial_prompt, jint maxTextSize, jint offset_ms, jint duration_ms) {
    UNUSED(thiz);
    auto *context = (struct whisper_context *) context_ptr;
    jfloat *audio_data_arr = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_data_length = env->GetArrayLength(audio_data);

    // Leave 2 processors free (i.e. the high-efficiency cores).
    int max_threads = max(1, min(8, get_nprocs() - 2));
    LOGI("Selecting %d threads", max_threads);

    const char *languageCodeChar = env->GetStringUTFChars(language_code, nullptr);
    const char *languageCodeToIgnoreChar = env->GetStringUTFChars(language_to_ignore, nullptr);
    const char *initial_prompt_char = env->GetStringUTFChars(initial_prompt, nullptr);

    // The below adapted from the Objective-C iOS sample
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    if (translate == JNI_TRUE) {
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "translate is active");
    } else {
        __android_log_print(ANDROID_LOG_INFO, "Whisper", "translate is not active");
    }

    params.token_timestamps = true;
    params.print_realtime = false;
    params.print_progress = true;
    params.print_timestamps = false;
    params.print_special = false;
    params.language = languageCodeChar;
    params.translate = translate;
    params.speed_up = speed;
    params.n_threads = max_threads;
    params.no_context = true;
    params.max_len = maxTextSize;
    params.n_max_text_ctx = 0;
    params.single_segment = false;
    params.split_on_word = true;
    params.progress_callback = myProgressCallback;
    params.progress_callback_user_data = env;
    params.new_segment_callback = mySegmentCallback;
    params.new_segment_callback_user_data = env;
    params.inferenceStoppedCallback = myAbortingCallback;
    params.inferenceStoppedCallback_user_data = env;
    params.beam_search.beam_size = 5;
    params.greedy.best_of = 3;
    params.temperature_inc = 0.2f;
    params.thold_pt = 0.02f;
    params.length_penalty = 1.0f;
    params.ignore_lang_id = languageCodeToIgnoreChar;
    params.initial_prompt = initial_prompt_char;
    params.offset_ms = offset_ms;
    params.duration_ms = duration_ms;
    //params.speed_up = true;

    whisper_reset_timings(context);

    LOGI("About to run whisper_full");
    LOGI("Audio data length: %zu", audio_data_length);
    int ret = whisper_full(context, params, audio_data_arr, audio_data_length);
    __android_log_print(ANDROID_LOG_INFO, "Whisper", "whisper_full returned %d", ret);
    if (ret < 0) {
        LOGI("Failed to run the model");
    } else {
        if (ret == 2) {
            __android_log_print(ANDROID_LOG_INFO, "Whisper", "Langue code: %s a été ignorée !", params.ignore_lang_id);
        }
        LOGI("Successfully run the model");
        whisper_print_timings(context);
    }
    env->ReleaseFloatArrayElements(audio_data, audio_data_arr, JNI_ABORT);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_getSeekDelta(JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto *context = (struct whisper_context *) context_ptr;
    return (jint) get_seek_delta(context);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_getResultLen(JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto *context = (struct whisper_context *) context_ptr;
    return (jint) get_result_len(context);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto *context = (struct whisper_context *) context_ptr;
    return whisper_full_n_segments(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    auto *context = (struct whisper_context *) context_ptr;
    const char *text = whisper_full_get_segment_text(context, index);
    jstring string = env->NewStringUTF(text);
    return string;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz
) {
    UNUSED(thiz);
    const char *sysinfo = whisper_print_system_info();
    jstring string = env->NewStringUTF(sysinfo);
    return string;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_benchMemcpy(JNIEnv *env, jobject thiz,
                                                                      jint n_threads) {
    UNUSED(thiz);
    const char *bench_ggml_memcpy = whisper_bench_memcpy_str(n_threads);
    jstring string = env->NewStringUTF(bench_ggml_memcpy);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_audio2text_WhisperLib_00024Companion_benchGgmlMulMat(JNIEnv *env, jobject thiz,
                                                                          jint n_threads) {
    UNUSED(thiz);
    const char *bench_ggml_mul_mat = whisper_bench_ggml_mul_mat_str(n_threads);
    jstring string = env->NewStringUTF(bench_ggml_mul_mat);
}
