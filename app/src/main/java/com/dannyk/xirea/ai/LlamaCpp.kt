package com.dannyk.xirea.ai

/**
 * JNI wrapper for llama.cpp native library.
 * This class provides the bridge between Kotlin and the native C++ code.
 */
class LlamaCpp {
    
    companion object {
        init {
            System.loadLibrary("xirea")
        }
    }
    
    /**
     * Load a GGUF model from the specified path.
     * 
     * @param modelPath Absolute path to the GGUF model file
     * @param nCtx Context size (max tokens in context window)
     * @param nThreads Number of CPU threads to use
     * @param nGpuLayers Number of layers to offload to GPU (0 for CPU-only)
     * @return true if model loaded successfully, false otherwise
     */
    external fun loadModel(
        modelPath: String,
        nCtx: Int = 2048,
        nThreads: Int = 4,
        nGpuLayers: Int = 0
    ): Boolean
    
    /**
     * Unload the currently loaded model and free resources.
     */
    external fun unloadModel()
    
    /**
     * Check if a model is currently loaded.
     */
    external fun isModelLoaded(): Boolean
    
    /**
     * Stop the current generation process.
     */
    external fun stopGeneration()
    
    /**
     * Generate text based on the given prompt.
     * Tokens are streamed via the callback as they're generated.
     * 
     * @param prompt The input prompt
     * @param maxTokens Maximum number of tokens to generate
     * @param callback Callback for receiving generated tokens
     * @return The complete generated response
     */
    external fun generate(
        prompt: String,
        maxTokens: Int = 512,
        callback: TokenCallback
    ): String

    /**
     * Format a conversation (system/user/assistant messages) into a single
     * prompt string using llama.cpp's own chat template machinery.
     *
     * If the loaded GGUF has an embedded chat template, that exact template
     * is used. If not, llama.cpp's own generic "chatml" fallback template is
     * used (this is llama.cpp's built-in fallback, not a per-model format
     * hardcoded here). Nothing about a specific model family - TinyLlama,
     * Gemma, Phi, Qwen, etc. - is hardcoded in this call.
     *
     * @param roles Parallel array of message roles, e.g. "system"/"user"/"assistant"
     * @param contents Parallel array of message contents (same length as [roles])
     * @param addAssistantPrompt Whether to append the tokens that open a new
     *   assistant turn (should be true when about to generate a reply)
     * @return The formatted prompt string, or an empty string if the template
     *   could not be applied (caller should fall back to a plain-text prompt
     *   in that case).
     */
    external fun formatChatPrompt(
        roles: Array<String>,
        contents: Array<String>,
        addAssistantPrompt: Boolean = true
    ): String
    
    /**
     * Get information about the loaded model.
     * Returns a JSON string with model details.
     */
    external fun getModelInfo(): String
    
    /**
     * Get the context size of the loaded model.
     */
    external fun getContextSize(): Long
    
    /**
     * Check if generation is currently in progress.
     */
    external fun isGenerating(): Boolean
    
    /**
     * Callback interface for receiving generated tokens.
     */
    interface TokenCallback {
        /**
         * Called when a new token is generated.
         * @param token The generated token text
         */
        fun onToken(token: String)
    }
}
