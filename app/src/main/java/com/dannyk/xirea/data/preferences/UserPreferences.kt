package com.dannyk.xirea.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {
    
    companion object {
        private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
    }
    
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME_KEY] ?: false
    }
    
    val selectedModelId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_MODEL_KEY]
    }
    
    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = isDark
        }
    }
    
    suspend fun setSelectedModel(modelId: String?) {
        context.dataStore.edit { preferences ->
            if (modelId != null) {
                preferences[SELECTED_MODEL_KEY] = modelId
            } else {
                preferences.remove(SELECTED_MODEL_KEY)
            }
        }
    }
}
