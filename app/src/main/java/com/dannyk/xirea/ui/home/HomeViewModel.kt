package com.dannyk.xirea.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dannyk.xirea.ai.AIEngine
import com.dannyk.xirea.data.model.Chat
import com.dannyk.xirea.data.model.Message
import com.dannyk.xirea.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatWithPreview(
    val chat: Chat,
    val lastMessage: Message?
)

data class HomeUiState(
    val chats: List<ChatWithPreview> = emptyList(),
    val isLoading: Boolean = true,
    val isModelLoaded: Boolean = false,
    val loadedModelName: String? = null,
    val showDeleteDialog: Boolean = false,
    val chatToDelete: Chat? = null,
    val showDeleteAllDialog: Boolean = false
)

class HomeViewModel(
    private val chatRepository: ChatRepository,
    private val aiEngine: AIEngine
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadChats()
        updateModelStatus()
    }
    
    private fun loadChats() {
        viewModelScope.launch {
            chatRepository.allChats.collect { chats ->
                val chatsWithPreview = chats.map { chat ->
                    val lastMessage = chatRepository.getLastMessageForChat(chat.id)
                    ChatWithPreview(chat, lastMessage)
                }
                _uiState.update { 
                    it.copy(
                        chats = chatsWithPreview,
                        isLoading = false
                    )
                }
            }
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
    
    fun showDeleteDialog(chat: Chat) {
        _uiState.update { it.copy(showDeleteDialog = true, chatToDelete = chat) }
    }
    
    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, chatToDelete = null) }
    }
    
    fun deleteChat() {
        val chat = _uiState.value.chatToDelete ?: return
        viewModelScope.launch {
            chatRepository.deleteChat(chat)
            hideDeleteDialog()
        }
    }
    
    fun showDeleteAllDialog() {
        _uiState.update { it.copy(showDeleteAllDialog = true) }
    }
    
    fun hideDeleteAllDialog() {
        _uiState.update { it.copy(showDeleteAllDialog = false) }
    }
    
    fun deleteAllChats() {
        viewModelScope.launch {
            chatRepository.deleteAllChats()
            hideDeleteAllDialog()
        }
    }
    
    suspend fun createNewChat(): Long {
        return chatRepository.createChat("New Chat")
    }
    
    class Factory(
        private val chatRepository: ChatRepository,
        private val aiEngine: AIEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(chatRepository, aiEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
