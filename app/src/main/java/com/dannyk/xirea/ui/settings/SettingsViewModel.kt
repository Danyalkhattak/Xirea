package com.dannyk.xirea.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dannyk.xirea.ai.AIEngine
import com.dannyk.xirea.data.preferences.UserPreferences
import com.dannyk.xirea.data.repository.ChatRepository
import com.dannyk.xirea.data.repository.ModelRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isDarkTheme: Boolean = false,
    val selectedModelName: String? = null,
    val chatCount: Int = 0,
    val modelsStorageSize: Long = 0,
    val totalStorageUsed: Long = 0,
    val showClearDataDialog: Boolean = false
)

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val aiEngine: AIEngine
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            // Load theme preference
            userPreferences.isDarkTheme.collect { isDark ->
                _uiState.update { it.copy(isDarkTheme = isDark) }
            }
        }
        
        viewModelScope.launch {
            // Load selected model
            val loadedModel = aiEngine.getLoadedModel()
            _uiState.update { it.copy(selectedModelName = loadedModel?.name) }
        }
        
        viewModelScope.launch {
            // Load chat count
            chatRepository.chatCount.collect { count ->
                _uiState.update { it.copy(chatCount = count) }
            }
        }
        
        viewModelScope.launch {
            // Load storage info
            val modelsSize = modelRepository.getModelsStorageSize()
            _uiState.update { 
                it.copy(
                    modelsStorageSize = modelsSize,
                    totalStorageUsed = modelsSize // Could add chat db size here
                )
            }
        }
    }
    
    fun toggleDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            userPreferences.setDarkTheme(isDark)
        }
    }
    
    fun showClearDataDialog() {
        _uiState.update { it.copy(showClearDataDialog = true) }
    }
    
    fun hideClearDataDialog() {
        _uiState.update { it.copy(showClearDataDialog = false) }
    }
    
    fun clearAllChatHistory() {
        viewModelScope.launch {
            chatRepository.deleteAllChats()
            hideClearDataDialog()
        }
    }
    
    fun refreshModelStatus() {
        val loadedModel = aiEngine.getLoadedModel()
        _uiState.update { it.copy(selectedModelName = loadedModel?.name) }
    }
    
    class Factory(
        private val userPreferences: UserPreferences,
        private val chatRepository: ChatRepository,
        private val modelRepository: ModelRepository,
        private val aiEngine: AIEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(userPreferences, chatRepository, modelRepository, aiEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
