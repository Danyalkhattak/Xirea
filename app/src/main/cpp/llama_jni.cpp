#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <algorithm>
#include <cctype>
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

// JVM reference for callbacks
static JavaVM* g_jvm = nullptr;

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
        LOGE("Failed to load model");
        return JNI_FALSE;
    }
    
    // Get vocabulary
    g_vocab = llama_model_get_vocab(g_model);
    if (g_vocab == nullptr) {
        LOGE("Failed to get vocabulary");
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
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    // Enforce model size and quantization constraints
    uint64_t n_params = llama_model_n_params(g_model);
    if (n_params > kMaxParams) {
        LOGE("Model too large: %llu params (max 7B)", (unsigned long long) n_params);
        llama_free(g_ctx);
        g_ctx = nullptr;
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    char desc[256];
    llama_model_desc(g_model, desc, sizeof(desc));
    std::string desc_str(desc);
    for (auto & c : desc_str) c = (char) tolower((unsigned char) c);
    if (desc_str.find("q4") == std::string::npos &&
        desc_str.find("q5") == std::string::npos &&
        desc_str.find("quantized") == std::string::npos) {
        LOGE("Unsupported quantization (require Q4/Q5): %s", desc);
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
    g_batch_initialized = true;
    
    // Initialize sampler with near-greedy settings for SPEED
    // Lower values = faster sampling, less randomness
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    
    // Near-greedy sampling chain for maximum speed
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(20));    // Very focused
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.85f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.6f));   // Low temp = faster
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    
    LOGI("Model loaded: ctx=%d, batch=%d, threads=%d (near-greedy sampling)",
         g_context_size, g_batch_size, g_n_threads);
    
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
    
    // Tokenize prompt
    std::vector<llama_token> tokens = tokenize_prompt(prompt_str, true);
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
    
    // Reset sampler state
    llama_sampler_reset(g_sampler);
    
    while (n_generated < maxTokens && n_cur < g_context_size && g_stop_generation_id.load() != local_id) {
        // Sample next token - sampler uses logits from last decode
        llama_token new_token = llama_sampler_sample(g_sampler, g_ctx, -1);
        
        // Check for end of generation (EOS token)
        if (llama_vocab_is_eog(g_vocab, new_token)) {
            LOGD("EOS token reached");
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
    info += "\"n_threads\":" + std::to_string(g_n_threads);
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

} // extern "C"
