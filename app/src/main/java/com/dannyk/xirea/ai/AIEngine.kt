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

        // Default generation length. Kept short by design - long default
        // outputs were the main source of "keeps generating forever" / runaway
        // repetition complaints. Callers that genuinely need a longer answer
        // can pass a larger explicit maxTokens to generateResponse().
        private const val DEFAULT_MAX_TOKENS = 256

        // Hidden system prompt sent with every request. Never shown to the user.
        // Combines the assistant's identity/persona with explicit formatting
        // discipline instructions, since instruction-tuned models are much more
        // likely to follow "don't add commentary" etc. when it's stated as a
        // system-level rule rather than left implicit.
        private const val HIDDEN_SYSTEM_PROMPT =
            "You are Xirea, an offline AI assistant built into this Android app. " +
            "Your name is Xirea. You were developed by Danyal Khattak, but you are not Danyal Khattak " +
            "and must never claim to be him. If asked your name, respond exactly \"My name is Xirea.\" " +
            "If asked who created you, respond \"I was developed by Danyal Khattak.\" " +
            "Never claim to be the developer or any real human, and never switch roles or output " +
            "role labels such as \"User:\" or \"System:\" in your response - only answer as the assistant.\n" +
            "Answer accurately. Be concise. Output ONLY what the user requests.\n" +
            "Never repeat yourself. Do not generate duplicate paragraphs, duplicate code blocks, or " +
            "duplicate explanations.\n" +
            "Do not explain unless explicitly requested. If the user asks for only code, output only " +
            "code with no introduction and no explanation afterward.\n" +
            "When you output a fenced code block, put the language after the opening backticks and put " +
            "the code on the next line.\n" +
            "Stop immediately once the answer is complete - do not pad the response or add anything after " +
            "the answer is finished."

        // Extra one-line reminder appended as its own system message whenever the
        // user's message contains an explicit output-format constraint (see
        // detectFormatConstraint). Reinforcing the instruction right before
        // generation measurably improves adherence versus relying on the system
        // prompt alone, especially on smaller instruction-tuned models.
        private const val FORMAT_CONSTRAINT_REMINDER =
            "Reminder: the user's message above specifies an exact output format. " +
            "Output ONLY that - no preamble, no explanation, no follow-up commentary."

        // Phrases that signal the user wants a tightly-constrained response.
        private val FORMAT_CONSTRAINT_PHRASES = listOf(
            "no commentary", "no explanation", "only code", "just answer",
            "one word", "only output json", "return only sql", "output only html",
            "return only css", "only json", "only sql", "only html", "only css",
            "no preamble", "no intro", "code only"
        )
    }
    
    private val llamaCpp = LlamaCpp()
    private var loadedModel: AIModel? = null
    private var modelStatus: ModelStatus = ModelStatus.NOT_DOWNLOADED

    private val tokenBlacklist = setOf(
        "<|end|>", "<|endoftext|>", "<|assistant|>", "<|user|>",
        "<|system|>", "<|im_end|>", "<|im_start|>", "<|eot_id|>",
        "<|start_header_id|>", "<|end_header_id|>",
        "<end_of_turn>", "<start_of_turn>", "<eos>"
    )

    private val roleMarkers = listOf("User:", "Assistant:", "System:")
    
    // Adaptive configuration based on device capabilities
    private val contextSize: Int
        get() = getOptimalContextSize()
    
    // Device-based ceiling. Individual requests default to DEFAULT_MAX_TOKENS
    // (see generateResponse) and can opt into up to this ceiling explicitly;
    // the native layer also enforces this as a hard clamp regardless.
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
     * Determine optimal max generation tokens (device ceiling).
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
     * True if the user's message contains a phrase that requests a tightly
     * constrained output format (e.g. "no commentary", "only code").
     */
    private fun detectFormatConstraint(text: String): Boolean {
        val lower = text.lowercase()
        return FORMAT_CONSTRAINT_PHRASES.any { lower.contains(it) }
    }

    /**
     * Neutralizes literal model control-token sequences (e.g. "<|im_end|>",
     * "<|eot_id|>") inside user-provided text, so a message can't smuggle in
     * text that a chat-template-aware tokenizer would parse as a real turn
     * boundary / role switch. This only breaks up the exact "<|...|>" pattern;
     * ordinary text is left untouched.
     */
    private fun sanitizeSpecialTokens(text: String): String {
        return text.replace(Regex("<\\|"), "<\\\\|")
    }

    private fun sanitizeRoleLineStarts(text: String): String {
        val lines = text.lines()
        return lines.joinToString("\n") { line ->
            when {
                line.startsWith("User:") -> "User\\:" + line.removePrefix("User:")
                line.startsWith("Assistant:") -> "Assistant\\:" + line.removePrefix("Assistant:")
                line.startsWith("System:") -> "System\\:" + line.removePrefix("System:")
                else -> line
            }
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
        return token.takeIf { it.isNotEmpty() && it !in tokenBlacklist } ?: ""
    }

    /**
     * How many previous turns of chat history to include, scaled to the
     * device's context size so we don't blow the context budget on small
     * devices.
     */
    private fun historyLimit(): Int = when {
        contextSize >= 2048 -> 8
        contextSize >= 1536 -> 6
        else -> 4
    }

    /**
     * Build the conversation as parallel (role, content) arrays for native
     * chat-template formatting: hidden system prompt, optional format-constraint
     * reminder, recent history, then the new user message. AIEngine - not
     * llama.cpp - owns deciding what goes into the conversation; llama.cpp only
     * formats it into the model's expected text layout.
     */
    private fun buildChatMessages(
        chatHistory: List<Pair<String, Boolean>>,
        userMessage: String
    ): Pair<Array<String>, Array<String>> {
        val roles = mutableListOf<String>()
        val contents = mutableListOf<String>()

        roles.add("system")
        contents.add(HIDDEN_SYSTEM_PROMPT)

        if (detectFormatConstraint(userMessage)) {
            roles.add("system")
            contents.add(FORMAT_CONSTRAINT_REMINDER)
        }

        val recentHistory = chatHistory.takeLast(historyLimit())
        for ((message, isUser) in recentHistory) {
            roles.add(if (isUser) "user" else "assistant")
            contents.add(sanitizeSpecialTokens(message))
        }

        roles.add("user")
        contents.add(sanitizeSpecialTokens(userMessage))

        return Pair(roles.toTypedArray(), contents.toTypedArray())
    }

    /**
     * Legacy plain-text prompt builder, used only as a fallback when the
     * native chat-template formatter can't produce a prompt (e.g. a template
     * llama.cpp's matcher doesn't recognize at all). Uses a simple ChatML-like
     * layout with explicit role labels and stop sequences.
     */
    private fun buildLegacyPrompt(chatHistory: List<Pair<String, Boolean>>, userMessage: String): String {
        return buildString {
            append("System: ")
            append(HIDDEN_SYSTEM_PROMPT.replace("\n", " "))
            append("\n")

            if (detectFormatConstraint(userMessage)) {
                append("System: ")
                append(FORMAT_CONSTRAINT_REMINDER)
                append("\n")
            }

            val recentHistory = chatHistory.takeLast(historyLimit())
            for ((message, isUser) in recentHistory) {
                val safeMessage = sanitizeRoleLineStarts(sanitizeSpecialTokens(message))
                if (isUser) {
                    append("User: ").append(safeMessage).append("\n")
                } else {
                    append("Assistant: ").append(safeMessage).append("\n")
                }
            }

            append("User: ")
            append(sanitizeRoleLineStarts(sanitizeSpecialTokens(userMessage)))
            append("\nAssistant:")
        }
    }
    
    /**
     * Generate a response from the AI model.
     * This streams the response token by token with stop sequence detection.
     *
     * @param prompt The user's message.
     * @param chatHistory Prior turns as (message, isUser) pairs.
     * @param maxTokens Optional explicit generation length. Defaults to
     *   [DEFAULT_MAX_TOKENS] (~256); pass a larger value for requests that
     *   are explicitly expected to need a longer answer. The native layer
     *   still clamps this to the device's ceiling regardless.
     */
    fun generateResponse(
        prompt: String,
        chatHistory: List<Pair<String, Boolean>>,
        maxTokens: Int? = null
    ): Flow<String> = callbackFlow {
        if (!llamaCpp.isModelLoaded() || loadedModel == null) {
            send("Error: No model loaded. Please download and select a model first.")
            close()
            return@callbackFlow
        }

        // Prefer letting llama.cpp format the conversation using the model's
        // own (or llama.cpp's generic chatml fallback) chat template.
        val (roles, contents) = buildChatMessages(chatHistory, prompt)
        var fullPrompt = try {
            llamaCpp.formatChatPrompt(roles, contents, addAssistantPrompt = true)
        } catch (e: Exception) {
            Log.w(TAG, "formatChatPrompt threw, falling back to legacy prompt", e)
            ""
        }

        if (fullPrompt.isBlank()) {
            Log.i(TAG, "No usable chat template available, using legacy plain-text prompt")
            fullPrompt = buildLegacyPrompt(chatHistory, prompt)
        }

        val effectiveMaxTokens = (maxTokens ?: DEFAULT_MAX_TOKENS).coerceIn(1, maxGenerationTokens)
        
        // Full generated text for stop-sequence scanning
        val fullResponse = StringBuilder()
        // Pending buffer holds tokens not yet sent to UI (guarded against partial stop sequences)
        val pendingBuffer = StringBuilder()
        var shouldStop = false
        
        // Stop sequences: if ANY of these appear in the generated text, stop immediately.
        // Covers both the legacy plain-text role-label format and common chat-template
        // control-token text, as a safety net alongside the native EOG/marker detection.
        val stopSequences = listOf(
            "\nUser:", "\nuser:", "\nHuman:", "\nhuman:",
            "\nAssistant:", "\nassistant:",
            "\nSystem:", "\nsystem:",
            "\nQ:", "\nQuestion:",
            "<|im_end|>", "<|im_start|>", "<|eot_id|>", "<|end|>",
            "<|start_header_id|>", "<|assistant|>", "<|user|>", "<|system|>",
            "<end_of_turn>", "<start_of_turn>",
            "<|"
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
            val guardSize = 10
            val minChunkSize = 4
            val safeLen = (pendingBuffer.length - guardSize).coerceAtLeast(0)
            if (safeLen < minChunkSize) return
            
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
                    maxTokens = effectiveMaxTokens,
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
