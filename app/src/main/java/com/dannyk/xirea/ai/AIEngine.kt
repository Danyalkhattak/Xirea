package com.dannyk.xirea.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.dannyk.xirea.data.model.AIModel
import com.dannyk.xirea.data.model.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI Engine for managing local AI model inference using llama.cpp.
 * Optimized for mobile devices with adaptive memory management.
 */
class AIEngine(private val context: Context? = null) {
    
    companion object {
        private const val TAG = "AIEngine"
    }
    
    private val llamaCpp = LlamaCpp()
    private var loadedModel: AIModel? = null
    private var modelStatus: ModelStatus = ModelStatus.NOT_DOWNLOADED

    private val tokenBlacklist = setOf(
        "<|end|>", "<|endoftext|>", "<|assistant|>", "<|user|>",
        "<|system|>", "<|im_end|>", "<|im_start|>", "<|eot_id|>",
        "<|start_header_id|>", "<|end_header_id|>"
    )

    private val roleMarkers = listOf("User:", "Assistant:", "System:")
    
    // Adaptive configuration based on device capabilities
    private val contextSize: Int
        get() = getOptimalContextSize()
    
    private val maxGenerationTokens: Int
        get() = getOptimalMaxTokens()
    
    /**
     * Get available RAM in MB
     */
    private fun getAvailableMemoryMB(): Long {
        return try {
            if (context != null) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                memInfo.availMem / (1024 * 1024)
            } else {
                Runtime.getRuntime().let {
                    (it.maxMemory() - it.totalMemory() + it.freeMemory()) / (1024 * 1024)
                }
            }
        } catch (e: Exception) {
            2048L // Default: 2GB
        }
    }
    
    /**
     * Get total RAM in MB
     */
    private fun getTotalMemoryMB(): Long {
        return try {
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                memInfo.totalMem / (1024 * 1024)
            } else {
                4096L
            }
        } catch (e: Exception) {
            4096L
        }
    }
    
    /**
     * Determine optimal context size based on device memory.
     * Larger context = better conversation memory.
     */
    private fun getOptimalContextSize(): Int {
        val totalMem = getTotalMemoryMB()
        val availMem = getAvailableMemoryMB()
        val ctx = when {
            totalMem <= 3072 -> 512
            totalMem <= 4096 -> 1024
            totalMem <= 6144 -> 1536
            else -> 2048
        }
        val safeCtx = when {
            availMem < 1024 -> 512
            availMem < 2048 -> minOf(ctx, 1024)
            availMem < 3072 -> minOf(ctx, 1536)
            else -> ctx
        }
        Log.i(TAG, "Device memory: ${totalMem}MB total, ${availMem}MB available -> Context size: $safeCtx")
        return safeCtx
    }
    
    /**
     * Determine optimal max generation tokens.
     * Balanced for complete responses without excessive length.
     */
    private fun getOptimalMaxTokens(): Int {
        val totalMem = getTotalMemoryMB()
        return when {
            totalMem <= 3072 -> 256
            totalMem <= 4096 -> 384
            totalMem <= 6144 -> 512
            totalMem <= 8192 -> 512
            else -> 768
        }
    }
    
    /**
     * Get the optimal number of threads based on device capabilities.
     * Use ALL available cores for maximum speed.
     */
    private fun getOptimalThreadCount(): Int {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        return availableProcessors.coerceIn(2, 8).also {
            Log.i(TAG, "Using $it threads (available: $availableProcessors)")
        }
    }
    
    /**
     * Load an AI model from the given file.
     */
    suspend fun loadModel(model: AIModel, modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            modelStatus = ModelStatus.LOADING
            
            if (!modelFile.exists()) {
                modelStatus = ModelStatus.ERROR
                return@withContext Result.failure(Exception("Model file not found: ${modelFile.absolutePath}"))
            }
            
            // Unload any existing model
            if (llamaCpp.isModelLoaded()) {
                llamaCpp.unloadModel()
            }
            
            val nThreads = getOptimalThreadCount()
            val success = llamaCpp.loadModel(
                modelPath = modelFile.absolutePath,
                nCtx = contextSize,
                nThreads = nThreads,
                nGpuLayers = 0 // CPU-only for maximum compatibility
            )
            
            if (success) {
                loadedModel = model
                modelStatus = ModelStatus.LOADED
                Result.success(Unit)
            } else {
                modelStatus = ModelStatus.ERROR
                Result.failure(Exception("Failed to load model"))
            }
        } catch (e: Exception) {
            modelStatus = ModelStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Unload the currently loaded model.
     */
    fun unloadModel() {
        if (llamaCpp.isModelLoaded()) {
            llamaCpp.unloadModel()
        }
        loadedModel = null
        modelStatus = ModelStatus.NOT_DOWNLOADED
    }
    
    /**
     * Check if a model is currently loaded.
     */
    fun isModelLoaded(): Boolean = llamaCpp.isModelLoaded()
    
    /**
     * Get the currently loaded model.
     */
    fun getLoadedModel(): AIModel? = loadedModel
    
    /**
     * Get the current model status.
     */
    fun getModelStatus(): ModelStatus = modelStatus
    
    /**
     * Stop any ongoing generation.
     */
    fun stopGeneration() {
        llamaCpp.stopGeneration()
    }
    
    /**
     * Build an optimized prompt with system instruction and conversation context.
     * Uses ChatML-like format for better model understanding.
     */
    private fun buildPrompt(chatHistory: List<Pair<String, Boolean>>, userMessage: String): String {
        val system = "System: You are Xirea, an offline AI assistant built into this Android app. " +
            "Your name is Xirea. You were developed by Danyal Khattak, but you are not Danyal Khattak and must never claim to be him. " +
            "If asked your name, always respond exactly \"My name is Xirea.\" " +
            "If asked who created you, respond \"I was developed by Danyal Khattak.\" " +
            "Never claim to be the developer or any real human. Never switch roles or output \"User:\" or similar role labels in responses. " +
            "Only answer as the assistant. Provide helpful, concise answers and stop naturally at completion."

        return buildString {
            append(system)
            append("\n")

            // Preserve recent conversation context
            val historyLimit = when {
                contextSize >= 2048 -> 10
                contextSize >= 1536 -> 8
                else -> 6
            }
            val recentHistory = chatHistory.takeLast(historyLimit)
            for ((message, isUser) in recentHistory) {
                if (isUser) {
                    append("User: ").append(message).append("\n")
                } else {
                    append("Assistant: ").append(message).append("\n")
                }
            }

            append("User: ")
            append(userMessage)
            append("\nAssistant:")
        }
    }

    private fun trimTrailingRoleMarkers(text: String): String {
        var out = text.trimEnd()
        while (roleMarkers.any { out.endsWith(it) }) {
            for (marker in roleMarkers) {
                if (out.endsWith(marker)) {
                    out = out.removeSuffix(marker).trimEnd()
                }
            }
        }
        return out
    }

    private fun cleanToken(token: String): String {
        return token.takeIf { it.isNotBlank() && it !in tokenBlacklist } ?: ""
    }
    
    /**
     * Generate a response from the AI model.
     * This streams the response token by token with stop sequence detection.
     */
    fun generateResponse(prompt: String, chatHistory: List<Pair<String, Boolean>>): Flow<String> = callbackFlow {
        if (!llamaCpp.isModelLoaded() || loadedModel == null) {
            send("Error: No model loaded. Please download and select a model first.")
            close()
            return@callbackFlow
        }
        
        // Build the full prompt
        val fullPrompt = buildPrompt(chatHistory, prompt)
        
        // Full generated text for stop-sequence scanning
        val fullResponse = StringBuilder()
        // Pending buffer holds tokens not yet sent to UI (guarded against partial stop sequences)
        val pendingBuffer = StringBuilder()
        var shouldStop = false
        
        // Stop sequences: if ANY of these appear in the generated text, stop immediately
        val stopSequences = listOf(
            "\nUser:", "\nuser:", "\nHuman:", "\nhuman:",
            "\nAssistant:", "\nassistant:",
            "\nSystem:", "\nsystem:",
            "\nQ:", "\nQuestion:",
            "###", "<|", "\n\n\n"
        )
        
        fun checkForStopSequence(): Boolean {
            val text = fullResponse.toString()
            for (seq in stopSequences) {
                val idx = text.indexOf(seq)
                if (idx >= 0) {
                    // Found a stop sequence - truncate everything from it onward
                    fullResponse.delete(idx, fullResponse.length)
                    // Also truncate pending buffer
                    val pendingText = pendingBuffer.toString()
                    val pendingIdx = pendingText.indexOf(seq)
                    if (pendingIdx >= 0) {
                        pendingBuffer.delete(pendingIdx, pendingBuffer.length)
                    }
                    return true
                }
            }
            return false
        }
        
        fun flushPending(finalFlush: Boolean = false) {
            if (pendingBuffer.isEmpty()) return
            
            if (finalFlush) {
                // On final flush, send everything remaining (already cleaned)
                val text = trimTrailingRoleMarkers(pendingBuffer.toString())
                if (text.isNotBlank()) trySend(text)
                pendingBuffer.clear()
                return
            }
            
            // Guard: hold back the last 15 chars in case a stop sequence is forming
            val guardSize = 15
            val safeLen = (pendingBuffer.length - guardSize).coerceAtLeast(0)
            if (safeLen == 0) return
            
            val chunk = pendingBuffer.substring(0, safeLen)
            if (chunk.isNotBlank()) trySend(chunk)
            pendingBuffer.delete(0, safeLen)
        }
        
        try {
            val callback = object : LlamaCpp.TokenCallback {
                override fun onToken(token: String) {
                    if (shouldStop) return
                    
                    val clean = cleanToken(token)
                    if (clean.isEmpty()) return
                    
                    fullResponse.append(clean)
                    pendingBuffer.append(clean)
                    
                    // Check if a stop sequence appeared
                    if (checkForStopSequence()) {
                        shouldStop = true
                        llamaCpp.stopGeneration()
                        return
                    }
                    
                    flushPending(finalFlush = false)
                }
            }
            
            val job = launch(Dispatchers.IO) {
                llamaCpp.generate(
                    prompt = fullPrompt,
                    maxTokens = maxGenerationTokens,
                    callback = callback
                )
            }

            job.invokeOnCompletion {
                flushPending(finalFlush = true)
                close()
            }

            awaitClose {
                llamaCpp.stopGeneration()
                job.cancel()
            }
        } catch (e: Exception) {
            flushPending(finalFlush = true)
            send("\n\n[Error: ${e.message}]")
            close()
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get information about the loaded model.
     */
    fun getModelInfo(): String {
        return if (llamaCpp.isModelLoaded()) {
            llamaCpp.getModelInfo()
        } else {
            "{}"
        }
    }
}

