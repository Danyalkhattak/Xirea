#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <algorithm>
#include <cctype>
#include <cstring>
#include <mutex>
#include <android/log.h>
#include <sys/sysinfo.h>

#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ============================================================================
// Global state - pre-allocated for maximum efficiency
// ============================================================================
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_sampler* g_sampler = nullptr;
static const llama_vocab* g_vocab = nullptr;
static std::atomic<bool> g_is_generating{false};
static std::atomic<uint64_t> g_generation_id{0};
static std::atomic<uint64_t> g_stop_generation_id{0};

// Pre-allocated reusable batch - NEVER allocate inside generation loop
static llama_batch g_batch;
static bool g_batch_initialized = false;
static int g_batch_size = 128;
static int g_context_size = 1024;
static int g_n_threads = 4;
static int g_max_gen_tokens = 256;

static const int kLowEndContext = 512;
static const int kMidContext = 1024;
static const int kMidHighContext = 1536;
static const int kHighContext = 2048;

static const int kLowEndBatch = 128;
static const int kHighBatch = 256;

static const int kLowEndMaxThreads = 4;
static const int kMaxThreads = 8;

static const int kLowEndMaxGenTokens = 256;
static const int kMidMaxGenTokens = 512;
static const int kHighMaxGenTokens = 768;
static const uint64_t kMaxParams = 7ULL * 1000ULL * 1000ULL * 1000ULL; // 7B

// ============================================================================
// Sampler tuning - kept as named constants so they're easy to retune later.
// Values chosen to curb repetition/rambling without making output erratic:
//   - repeat penalty ~1.15 over the last 64 tokens is llama.cpp's own commonly
//     recommended "sane default" range for instruction models.
//   - small (0.05) frequency/presence penalties add a mild nudge toward
//     concise, non-repetitive output without materially increasing randomness.
// ============================================================================
static const int32_t kRepeatLastN      = 64;
static const float   kRepeatPenalty    = 1.15f;
static const float   kFrequencyPenalty = 0.05f;
static const float   kPresencePenalty  = 0.05f;

// JVM reference for callbacks
static JavaVM* g_jvm = nullptr;

// ============================================================================
// Global error reporting - thread safe
// ============================================================================
static std::string g_last_error;
static std::mutex g_error_mutex;

static void SetLastError(const std::string& msg) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    g_last_error = msg;
    LOGE("%s", msg.c_str());
}

// ============================================================================
// Safe, fixed configuration for broad compatibility
// ============================================================================
static long getTotalMemoryMB() {
    struct sysinfo info;
    if (sysinfo(&info) == 0) {
        return (info.totalram * info.mem_unit) / (1024 * 1024);
    }
    return 4096;
}

static int getThreadCount(bool lowEnd) {
    int cpuCores = std::thread::hardware_concurrency();
    if (cpuCores <= 0) cpuCores = 1;
    int cap = lowEnd ? kLowEndMaxThreads : kMaxThreads;
    return std::min(cpuCores, cap);
}

static void applyDeviceConfig() {
    const long totalMB = getTotalMemoryMB();
    const bool lowEnd = totalMB <= 3072;

    if (totalMB <= 3072) {
        g_context_size = kLowEndContext;
        g_batch_size = kLowEndBatch;
        g_max_gen_tokens = kLowEndMaxGenTokens;
    } else if (totalMB <= 4096) {
        g_context_size = kMidContext;
        g_batch_size = kHighBatch;
        g_max_gen_tokens = 384;
    } else if (totalMB <= 6144) {
        g_context_size = kMidHighContext;
        g_batch_size = kHighBatch;
        g_max_gen_tokens = kMidMaxGenTokens;
    } else if (totalMB <= 8192) {
        g_context_size = kHighContext;
        g_batch_size = kHighBatch;
        g_max_gen_tokens = kMidMaxGenTokens;
    } else {
        g_context_size = kHighContext;
        g_batch_size = kHighBatch;
        g_max_gen_tokens = kHighMaxGenTokens;
    }

    g_n_threads = getThreadCount(lowEnd);

    LOGI("Device config: RAM=%ldMB -> ctx=%d, batch=%d, threads=%d, maxTokens=%d",
         totalMB, g_context_size, g_batch_size, g_n_threads, g_max_gen_tokens);
}

// ============================================================================
// Tokenization helpers
// ============================================================================

// Detects whether `prompt` already begins with the model's literal BOS token
// text (e.g. some chat templates render "<bos>" / "<s>" / "<|begin_of_text|>"
// directly into the formatted string). If so, we must NOT also pass
// add_special=true to llama_tokenize(), or the prompt ends up with two BOS
// tokens back to back. This mirrors a warning llama.cpp's own tokenizer logs
// internally ("...prompt also starts with a BOS token...now starts with 2 BOS
// tokens") - we avoid triggering that condition in the first place.
static bool prompt_already_has_bos_text(const std::string& prompt) {
    if (g_vocab == nullptr) return false;
    llama_token bos = llama_vocab_bos(g_vocab);
    if (bos == LLAMA_TOKEN_NULL) return false;
    const char* bosText = llama_vocab_get_text(g_vocab, bos);
    if (bosText == nullptr || bosText[0] == '\0') return false;
    size_t bosLen = std::strlen(bosText);
    if (prompt.size() < bosLen) return false;
    return prompt.compare(0, bosLen, bosText) == 0;
}

static std::vector<llama_token> tokenize_prompt(const std::string& text, bool add_special) {
    if (g_vocab == nullptr) return {};
    
    int n_tokens = text.length() + 32;
    std::vector<llama_token> tokens(n_tokens);
    
    int actual = llama_tokenize(g_vocab, text.c_str(), text.length(), 
                                tokens.data(), n_tokens, add_special, true);
    
    if (actual < 0) {
        tokens.resize(-actual);
        actual = llama_tokenize(g_vocab, text.c_str(), text.length(),
                                tokens.data(), tokens.size(), add_special, true);
    }
    
    if (actual < 0) return {};
    tokens.resize(actual);
    return tokens;
}

// ============================================================================
// Stop / repetition detection helpers (used during token generation)
// ============================================================================

// Text-level stop markers. These complement EOG-token detection
// (llama_vocab_is_eog, checked first and preferred) for the rare case where a
// model's control tokens aren't registered as EOG in its GGUF metadata, or a
// chat template emits a textual marker that isn't tokenized as a single
// special token. This is a heuristic safety net, not the primary mechanism.
static const std::vector<std::string>& stopMarkers() {
    static const std::vector<std::string> markers = {
        "<|end|>", "<|endoftext|>", "<|eot_id|>", "<|im_end|>", "<|im_start|>",
        "<|assistant|>", "<|user|>", "<|system|>",
        "<|start_header_id|>", "<|end_header_id|>",
        "<end_of_turn>", "<start_of_turn>",
        "</s>", "<eos>"
    };
    return markers;
}

// Scans only the tail of `text` (last `scanFromTail` chars) for the earliest
// occurrence of any stop marker. Returns std::string::npos if none found.
static size_t find_stop_marker(const std::string& text, size_t scanFromTail) {
    if (text.empty()) return std::string::npos;
    size_t start = text.size() > scanFromTail ? text.size() - scanFromTail : 0;
    size_t best = std::string::npos;
    for (const auto& marker : stopMarkers()) {
        size_t pos = text.find(marker, start);
        if (pos != std::string::npos && (best == std::string::npos || pos < best)) {
            best = pos;
        }
    }
    return best;
}

// Heuristic: detects whether the tail of `text` consists of two identical
// adjacent chunks (i.e. the model just generated the same sentence/
// paragraph/code block twice in a row with no variation). Only catches EXACT
// immediate duplication - not paraphrased repeats - by design, to keep the
// false-positive rate low for legitimate short repeats (e.g. "---", "1. 2.").
static bool has_immediate_repetition(const std::string& text) {
    static const size_t kWindows[] = {24, 48, 96, 192};
    for (size_t w : kWindows) {
        if (text.size() < w * 2) continue;
        const char* tail = text.c_str() + text.size() - w;
        const char* prevTail = text.c_str() + text.size() - 2 * w;
        if (std::memcmp(tail, prevTail, w) == 0) {
            return true;
        }
    }
    return false;
}

// ============================================================================
// Batch helper - reuses pre-allocated batch
// ============================================================================
static void batch_clear() {
    g_batch.n_tokens = 0;
}

static void batch_add(llama_token token, int pos, bool logits) {
    int idx = g_batch.n_tokens;
    g_batch.token[idx] = token;
    g_batch.pos[idx] = pos;
    g_batch.n_seq_id[idx] = 1;
    g_batch.seq_id[idx][0] = 0;
    g_batch.logits[idx] = logits;
    g_batch.n_tokens++;
}

// ============================================================================
// JNI Lifecycle
// ============================================================================
extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    llama_backend_init();
    LOGI("Llama backend initialized");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    llama_backend_free();
    LOGI("Llama backend freed");
}

// ============================================================================
// Model Loading - Optimized for mobile
// ============================================================================
JNIEXPORT jboolean JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_loadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint nCtx,
    jint nThreads,
    jint nGpuLayers
) {
    // Clear any previous error
    SetLastError("");

    // Clean up any existing state
    if (g_batch_initialized) {
        llama_batch_free(g_batch);
        g_batch_initialized = false;
    }
    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        SetLastError("Invalid model path (null pointer)");
        return JNI_FALSE;
    }
    LOGI("Loading model: %s", path);
    
    // Adaptive configuration for device capabilities
    applyDeviceConfig();
    
    // Model parameters - optimized for mobile
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;      // CPU-only for stability
    model_params.use_mmap = true;       // Memory-mapped loading (fast, low memory)
    model_params.use_mlock = false;     // Don't lock - prevents OOM
    
    // Load model
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);
    
    if (g_model == nullptr) {
        std::string err = "Failed to load model from file: ";
        err += path ? path : "(null)";
        err += ". Possible causes: file does not exist, invalid GGUF format, corrupted model, unsupported architecture, or insufficient memory.";
        SetLastError(err);
        return JNI_FALSE;
    }
    
    // Get vocabulary
    g_vocab = llama_model_get_vocab(g_model);
    if (g_vocab == nullptr) {
        SetLastError("The model was opened but its vocabulary could not be loaded. The GGUF file may be corrupted.");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    
            // Cap by requested and model limits
                int modelTrainCtx = llama_model_n_ctx_train(g_model);
                int deviceCtx = g_context_size;
                g_context_size = std::min({(int)nCtx, deviceCtx, modelTrainCtx});
    
                LOGI("Context size: requested=%d, device=%d, model_max=%d -> using=%d",
                    nCtx, deviceCtx, modelTrainCtx, g_context_size);
    
    // Context parameters - performance optimized
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = g_context_size;
    ctx_params.n_threads = g_n_threads;
    ctx_params.n_threads_batch = g_n_threads;
    ctx_params.n_batch = g_batch_size;
    ctx_params.n_ubatch = g_batch_size;
    ctx_params.embeddings = false;      // Not needed for inference
    
    // Create context
    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        SetLastError("Unable to create llama context. This is usually caused by insufficient RAM or an invalid model.");
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    // Enforce model size constraints
    uint64_t n_params = llama_model_n_params(g_model);
    if (n_params > kMaxParams) {
        SetLastError("Model contains " + std::to_string(n_params) + " parameters. Maximum supported by this application is 7B.");
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    // Pre-allocate reusable batch - this is the KEY optimization
    // Never allocate inside the generation loop!
    g_batch = llama_batch_init(g_batch_size, 0, 1);
    // Simple sanity check: if batch size > 0 but token pointer is null, allocation failed
    if (g_batch_size > 0 && g_batch.token == nullptr) {
        SetLastError("Failed to allocate batch memory.");
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    g_batch_initialized = true;
    
    // Initialize sampler chain.
    // Order matters: penalties are applied to the raw logits first (matching
    // llama.cpp's own default chain order of penalties -> top_k -> top_p ->
    // temperature -> dist), so repeated/over-used tokens are suppressed
    // before top-k/top-p truncate the candidate pool.
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    if (g_sampler == nullptr) {
        SetLastError("Failed to initialize sampler chain.");
        llama_batch_free(g_batch);
        g_batch_initialized = false;
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    // Repeat/frequency/presence penalties - curbs the "repeats forever" failure mode.
    auto* penalties = llama_sampler_init_penalties(
        kRepeatLastN, kRepeatPenalty, kFrequencyPenalty, kPresencePenalty);
    if (penalties == nullptr) {
        SetLastError("Failed to initialize penalties sampler.");
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
        llama_batch_free(g_batch);
        g_batch_initialized = false;
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    llama_sampler_chain_add(g_sampler, penalties);
    
    // Near-greedy sampling chain for maximum speed
    // These additions are unlikely to fail, but we check for consistency
    auto* top_k = llama_sampler_init_top_k(20);
    if (top_k == nullptr) {
        SetLastError("Failed to initialize top-k sampler.");
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
        llama_batch_free(g_batch);
        g_batch_initialized = false;
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    llama_sampler_chain_add(g_sampler, top_k);
    
    auto* top_p = llama_sampler_init_top_p(0.85f, 1);
    if (top_p == nullptr) {
        SetLastError("Failed to initialize top-p sampler.");
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
        llama_batch_free(g_batch);
        g_batch_initialized = false;
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    llama_sampler_chain_add(g_sampler, top_p);
    
    auto* temp = llama_sampler_init_temp(0.6f);
    if (temp == nullptr) {
        SetLastError("Failed to initialize temperature sampler.");
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
        llama_batch_free(g_batch);
        g_batch_initialized = false;
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    llama_sampler_chain_add(g_sampler, temp);
    
    auto* dist = llama_sampler_init_dist(LLAMA_DEFAULT_SEED);
    if (dist == nullptr) {
        SetLastError("Failed to initialize distribution sampler.");
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
        llama_batch_free(g_batch);
        g_batch_initialized = false;
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    llama_sampler_chain_add(g_sampler, dist);
    
    LOGI("Model loaded: ctx=%d, batch=%d, threads=%d (penalties: last_n=%d repeat=%.2f freq=%.2f present=%.2f)",
         g_context_size, g_batch_size, g_n_threads,
         kRepeatLastN, kRepeatPenalty, kFrequencyPenalty, kPresencePenalty);
    
    return JNI_TRUE;
}

// ============================================================================
// Model Unloading
// ============================================================================
JNIEXPORT void JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_unloadModel(
    JNIEnv* env,
    jobject /* this */
) {
    g_stop_generation_id.store(g_generation_id.load());
    
    // Nullify pointers first to prevent stale access from other threads
    auto* batch_copy = g_batch_initialized ? &g_batch : nullptr;
    auto* sampler_copy = g_sampler;
    auto* ctx_copy = g_ctx;
    auto* model_copy = g_model;
    
    g_vocab = nullptr;
    g_sampler = nullptr;
    g_ctx = nullptr;
    g_model = nullptr;
    
    if (g_batch_initialized) {
        llama_batch_free(g_batch);
        g_batch_initialized = false;
    }
    if (sampler_copy != nullptr) {
        llama_sampler_free(sampler_copy);
    }
    if (ctx_copy != nullptr) {
        llama_free(ctx_copy);
    }
    if (model_copy != nullptr) {
        llama_model_free(model_copy);
    }
    
    LOGI("Model unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_isModelLoaded(
    JNIEnv* env,
    jobject /* this */
) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_stopGeneration(
    JNIEnv* env,
    jobject /* this */
) {
    g_stop_generation_id.store(g_generation_id.load());
}

// ============================================================================
// Chat template formatting
// ============================================================================
// Builds a properly formatted conversation string using llama.cpp's own chat
// template machinery (llama_chat_apply_template), which:
//   - uses the GGUF's embedded chat template when the model has one, and
//   - otherwise falls back to llama.cpp's own generic "chatml" template name
//     (this is llama.cpp's own built-in fallback identifier, not a per-model
//     format we invented here).
// Returns an empty string if formatting is not possible for any reason, so
// the Kotlin caller can fall back to its own plain-text prompt builder.
JNIEXPORT jstring JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_formatChatPrompt(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray roles,
    jobjectArray contents,
    jboolean addAssistant
) {
    if (g_model == nullptr) {
        return env->NewStringUTF("");
    }

    jsize n = env->GetArrayLength(roles);
    jsize nc = env->GetArrayLength(contents);
    if (n <= 0 || n != nc) {
        return env->NewStringUTF("");
    }

    // Copy role/content strings into owned std::string storage first, since
    // llama_chat_message only holds raw `const char*` pointers and JNI
    // string chars must not be released while those pointers are in use.
    std::vector<std::string> roleStrings;
    std::vector<std::string> contentStrings;
    roleStrings.reserve(n);
    contentStrings.reserve(n);

    for (jsize i = 0; i < n; i++) {
        auto jrole = (jstring) env->GetObjectArrayElement(roles, i);
        auto jcontent = (jstring) env->GetObjectArrayElement(contents, i);

        if (jrole != nullptr) {
            const char* roleChars = env->GetStringUTFChars(jrole, nullptr);
            roleStrings.emplace_back(roleChars != nullptr ? roleChars : "");
            if (roleChars != nullptr) env->ReleaseStringUTFChars(jrole, roleChars);
            env->DeleteLocalRef(jrole);
        } else {
            roleStrings.emplace_back("");
        }

        if (jcontent != nullptr) {
            const char* contentChars = env->GetStringUTFChars(jcontent, nullptr);
            contentStrings.emplace_back(contentChars != nullptr ? contentChars : "");
            if (contentChars != nullptr) env->ReleaseStringUTFChars(jcontent, contentChars);
            env->DeleteLocalRef(jcontent);
        } else {
            contentStrings.emplace_back("");
        }
    }

    std::vector<llama_chat_message> messages;
    messages.reserve(n);
    for (jsize i = 0; i < n; i++) {
        messages.push_back(llama_chat_message{ roleStrings[i].c_str(), contentStrings[i].c_str() });
    }

    // Prefer the model's own embedded chat template (from GGUF metadata).
    const char* embeddedTmpl = llama_model_chat_template(g_model, nullptr);
    std::string tmplStr = (embeddedTmpl != nullptr) ? std::string(embeddedTmpl) : std::string("chatml");

    // First pass: ask how many bytes are needed (buf=nullptr, length=0 is the
    // documented/officially-used pattern for this call).
    int32_t needed = llama_chat_apply_template(
        tmplStr.c_str(), messages.data(), messages.size(),
        addAssistant == JNI_TRUE, nullptr, 0);

    if (needed < 0) {
        LOGE("llama_chat_apply_template: template not recognized, caller should fall back");
        return env->NewStringUTF("");
    }

    std::vector<char> buf(static_cast<size_t>(needed) + 1, '\0');
    int32_t written = llama_chat_apply_template(
        tmplStr.c_str(), messages.data(), messages.size(),
        addAssistant == JNI_TRUE, buf.data(), (int32_t) buf.size());

    if (written < 0 || written > (int32_t) buf.size()) {
        LOGE("llama_chat_apply_template: second pass failed (written=%d, buf=%zu)", written, buf.size());
        return env->NewStringUTF("");
    }

    std::string result(buf.data(), written);
    return env->NewStringUTF(result.c_str());
}

// ============================================================================
// Token Generation - Maximum Speed Optimization
// ============================================================================
JNIEXPORT jstring JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_generate(
    JNIEnv* env,
    jobject /* this */,
    jstring prompt,
    jint maxTokens,
    jobject callback
) {
    if (g_model == nullptr || g_ctx == nullptr || g_vocab == nullptr || !g_batch_initialized) {
        return env->NewStringUTF("Error: Model not loaded");
    }
    
    if (g_is_generating.exchange(true)) {
        return env->NewStringUTF("Error: Generation already in progress");
    }
    
    const uint64_t local_id = g_generation_id.fetch_add(1) + 1;
    g_stop_generation_id.store(0);
    
    // Get prompt string
    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(prompt_cstr);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    
    // Clamp max tokens for stability based on device class
    if (maxTokens > g_max_gen_tokens) maxTokens = g_max_gen_tokens;
    if (maxTokens < 1) maxTokens = 1;

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = callbackClass
        ? env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V")
        : nullptr;
    if (callbackClass == nullptr || onTokenMethod == nullptr) {
        if (callbackClass != nullptr) env->DeleteLocalRef(callbackClass);
        g_is_generating = false;
        return env->NewStringUTF("{\"error\":\"Token callback not available\"}");
    }
    
    // Tokenize prompt. Skip llama_tokenize's automatic BOS insertion if the
    // prompt text already begins with the model's literal BOS token text
    // (some chat templates render it directly), to avoid a doubled BOS.
    bool addSpecial = !prompt_already_has_bos_text(prompt_str);
    std::vector<llama_token> tokens = tokenize_prompt(prompt_str, addSpecial);
    if (tokens.empty()) {
        env->DeleteLocalRef(callbackClass);
        g_is_generating = false;
        return env->NewStringUTF("Error: Tokenization failed");
    }
    
    int n_prompt = tokens.size();
    LOGD("Prompt: %d tokens", n_prompt);
    
    // === CRITICAL: Clear KV cache before EVERY generation ===
    llama_memory_t mem = llama_get_memory(g_ctx);
    if (mem) {
        llama_memory_clear(mem, true);
    }
    
    // Truncate prompt if too long (keep the end - more relevant)
    int max_prompt = std::max(0, g_context_size - maxTokens - 16);
    if (n_prompt > max_prompt) {
        tokens.erase(tokens.begin(), tokens.begin() + (n_prompt - max_prompt));
        n_prompt = tokens.size();
        LOGI("Prompt truncated to %d tokens", n_prompt);
    }
    
    // === Evaluate prompt in chunks using pre-allocated batch ===
    int n_processed = 0;
    
    while (n_processed < n_prompt && g_stop_generation_id.load() != local_id) {
        batch_clear();
        
        int n_batch = std::min(g_batch_size, n_prompt - n_processed);
        for (int i = 0; i < n_batch; i++) {
            int pos = n_processed + i;
            // Only compute logits for the LAST token of the LAST batch
            bool is_last = (pos == n_prompt - 1);
            batch_add(tokens[pos], pos, is_last);
        }
        
        if (llama_decode(g_ctx, g_batch) != 0) {
            env->DeleteLocalRef(callbackClass);
            g_is_generating = false;
            LOGE("Decode failed at position %d", n_processed);
            return env->NewStringUTF("Error: Prompt evaluation failed");
        }
        
        n_processed += n_batch;
    }
    
    if (g_stop_generation_id.load() == local_id) {
        env->DeleteLocalRef(callbackClass);
        g_is_generating = false;
        return env->NewStringUTF("");
    }
    
    LOGD("Prompt evaluated, starting generation");
    
    // === Token generation loop - optimized for speed ===
    std::string response;
    response.reserve(maxTokens * 8); // Pre-allocate response buffer
    
    int n_cur = n_prompt;
    int n_generated = 0;
    
    // Reset sampler state (also clears the penalty sampler's token history,
    // so repeat penalties only look at THIS generation, not a previous one)
    llama_sampler_reset(g_sampler);
    
    while (n_generated < maxTokens && n_cur < g_context_size && g_stop_generation_id.load() != local_id) {
        // Sample next token - sampler uses logits from last decode
        llama_token new_token = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Let the penalties sampler know this token was chosen, so future
        // samples in this generation are penalized for reusing it.
        llama_sampler_accept(g_sampler, new_token);
        
        // Check for end of generation (EOS token) - this is the primary,
        // reliable stop mechanism since it comes from the model/GGUF itself.
        if (llama_vocab_is_eog(g_vocab, new_token)) {
            LOGD("EOS/EOG token reached");
            break;
        }
        
        // Convert token to text using dynamic string to avoid overflow
        std::string token_str(128, '\0');
        int n = llama_token_to_piece(g_vocab, new_token, token_str.data(), (int)token_str.size() - 1, 0, true);
        if (n < 0) {
            // Buffer too small - resize and retry
            token_str.resize(-n + 1, '\0');
            n = llama_token_to_piece(g_vocab, new_token, token_str.data(), (int)token_str.size() - 1, 0, true);
        }
        if (n > 0) {
            token_str.resize(n);
            response.append(token_str);

            // Text-level stop marker check (safety net for markers that
            // aren't tokenized as a dedicated EOG token - see stopMarkers()).
            size_t markerPos = find_stop_marker(response, response.size() < 64 ? response.size() : 64);
            bool stoppedOnMarker = false;
            if (markerPos != std::string::npos) {
                response.erase(markerPos);
                LOGD("Text stop marker reached, truncating response");
                stoppedOnMarker = true;
            }

            // Immediate-repetition guard: stops runaway loops where the
            // model repeats the same sentence/paragraph/code verbatim.
            // Only checked once enough text exists to avoid false positives
            // on short, legitimate repeats.
            bool stoppedOnRepetition = false;
            if (!stoppedOnMarker && n_generated > 24 && has_immediate_repetition(response)) {
                LOGI("Immediate repetition detected after %d tokens, stopping generation", n_generated);
                stoppedOnRepetition = true;
            }

            if (stoppedOnMarker || stoppedOnRepetition) {
                break;
            }

            // === Stream token immediately to UI ===
            jstring jtoken = env->NewStringUTF(token_str.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jtoken);
            env->DeleteLocalRef(jtoken);
        }
        
        // === Decode next token using pre-allocated batch ===
        batch_clear();
        batch_add(new_token, n_cur, true);
        
        if (llama_decode(g_ctx, g_batch) != 0) {
            LOGE("Decode failed during generation");
            break;
        }
        
        n_cur++;
        n_generated++;
    }
    
    LOGI("Generated %d tokens", n_generated);
    env->DeleteLocalRef(callbackClass);
    g_is_generating = false;
    
    return env->NewStringUTF(response.c_str());
}

// ============================================================================
// Model Info
// ============================================================================
JNIEXPORT jstring JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_getModelInfo(
    JNIEnv* env,
    jobject /* this */
) {
    if (g_model == nullptr || g_vocab == nullptr) {
        return env->NewStringUTF("{}");
    }
    
    char buf[256];
    llama_model_desc(g_model, buf, sizeof(buf));
    
    std::string info = "{";
    info += "\"description\":\"" + std::string(buf) + "\",";
    info += "\"n_params\":" + std::to_string(llama_model_n_params(g_model)) + ",";
    info += "\"n_vocab\":" + std::to_string(llama_vocab_n_tokens(g_vocab)) + ",";
    info += "\"n_ctx_train\":" + std::to_string(llama_model_n_ctx_train(g_model)) + ",";
    info += "\"n_ctx\":" + std::to_string(g_context_size) + ",";
    info += "\"n_batch\":" + std::to_string(g_batch_size) + ",";
    info += "\"n_threads\":" + std::to_string(g_n_threads) + ",";
    info += "\"has_chat_template\":" + std::string(llama_model_chat_template(g_model, nullptr) != nullptr ? "true" : "false");
    info += "}";
    
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_getContextSize(
    JNIEnv* env,
    jobject /* this */
) {
    return g_context_size;
}

JNIEXPORT jboolean JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_isGenerating(
    JNIEnv* env,
    jobject /* this */
) {
    return g_is_generating ? JNI_TRUE : JNI_FALSE;
}

// ============================================================================
// Error Reporting
// ============================================================================
JNIEXPORT jstring JNICALL
Java_com_dannyk_xirea_ai_LlamaCpp_getLastError(
    JNIEnv* env,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

} // extern "C"
