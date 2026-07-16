package com.dannyk.xirea.ui.chat

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dannyk.xirea.ai.AIEngine
import com.dannyk.xirea.data.model.Chat
import com.dannyk.xirea.data.model.Message
import com.dannyk.xirea.data.repository.ChatRepository
import com.dannyk.xirea.data.repository.ModelRepository
import com.dannyk.xirea.util.ResponseNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val currentChat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val isGenerating: Boolean = false,
    val currentGeneratingText: String = "",
    val isModelLoaded: Boolean = false,
    val loadedModelName: String? = null,
    val error: String? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val aiEngine: AIEngine
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var currentChatId: Long? = null
    
    init {
        updateModelStatus()
    }
    
    fun loadChat(chatId: Long) {
        currentChatId = chatId
        viewModelScope.launch {
            val chat = chatRepository.getChatById(chatId)
            _uiState.update { it.copy(currentChat = chat) }
            
            chatRepository.getMessagesForChat(chatId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }
    
    fun createNewChat(): Long {
        var newChatId: Long = -1
        viewModelScope.launch {
            newChatId = chatRepository.createChat("New Chat")
            currentChatId = newChatId
            loadChat(newChatId)
        }
        return newChatId
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            // Add user message
            chatRepository.addMessage(chatId, content.trim(), isFromUser = true)
            
            // Update chat title if it's the first message
            val chat = chatRepository.getChatById(chatId)
            if (chat?.title == "New Chat") {
                val newTitle = content.take(30) + if (content.length > 30) "..." else ""
                chatRepository.updateChat(chat.copy(title = newTitle))
            }
            
            // Generate AI response
            generateAIResponse(content.trim())
        }
    }
    
    private fun generateAIResponse(prompt: String) {
        val chatId = currentChatId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, currentGeneratingText = "") }
            
            val chatHistory = _uiState.value.messages.map { it.content to it.isFromUser }
            
            val responseBuilder = StringBuilder()
            var lastUiUpdate = 0L
            
            aiEngine.generateResponse(prompt, chatHistory).collect { token ->
                responseBuilder.append(token)
                val now = SystemClock.elapsedRealtime()
                if (now - lastUiUpdate >= 33) {
                    lastUiUpdate = now
                    _uiState.update { it.copy(currentGeneratingText = responseBuilder.toString()) }
                }
            }
            
            val fullResponse = withContext(Dispatchers.Default) {
                ResponseNormalizer.cleanAssistantResponse(responseBuilder.toString())
            }
            
            // Only save if we have actual content
            if (fullResponse.isNotEmpty()) {
                chatRepository.addMessage(chatId, fullResponse, isFromUser = false)
            }
            
            _uiState.update { it.copy(isGenerating = false, currentGeneratingText = "") }
        }
    }

    
    fun updateModelStatus() {
        val isLoaded = aiEngine.isModelLoaded()
        val modelName = aiEngine.getLoadedModel()?.name
        _uiState.update { 
            it.copy(
                isModelLoaded = isLoaded,
                loadedModelName = modelName
            )
        }
    }

    fun stopGeneration() {
        aiEngine.stopGeneration()
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    class Factory(
        private val chatRepository: ChatRepository,
        private val modelRepository: ModelRepository,
        private val aiEngine: AIEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(chatRepository, modelRepository, aiEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
