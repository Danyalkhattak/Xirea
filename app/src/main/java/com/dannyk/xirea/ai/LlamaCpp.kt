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
