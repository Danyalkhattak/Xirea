package com.dannyk.xirea.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dannyk.xirea.ui.theme.ErrorColor
import com.dannyk.xirea.util.StorageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.refreshModelStatus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance section
            SettingsSectionHeader(title = "Appearance")
            
            SettingsItem(
                icon = Icons.Outlined.DarkMode,
                title = "Dark Theme",
                subtitle = if (uiState.isDarkTheme) "Enabled" else "Disabled",
                trailing = {
                    Switch(
                        checked = uiState.isDarkTheme,
                        onCheckedChange = { viewModel.toggleDarkTheme(it) }
                    )
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            
            // AI Model section
            SettingsSectionHeader(title = "AI Model")
            
            SettingsItem(
                icon = Icons.Outlined.Memory,
                title = "Selected Model",
                subtitle = uiState.selectedModelName ?: "No model selected",
                onClick = onNavigateToModels,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            
            // Storage section
            SettingsSectionHeader(title = "Storage")
            
            SettingsItem(
                icon = Icons.Outlined.Folder,
                title = "Models Storage",
                subtitle = StorageUtils.formatFileSize(uiState.modelsStorageSize),
                onClick = onNavigateToModels
            )
            
            SettingsItem(
                icon = Icons.Outlined.Chat,
                title = "Chat History",
                subtitle = "${uiState.chatCount} conversation${if (uiState.chatCount != 1) "s" else ""}"
            )
            
            SettingsItem(
                icon = Icons.Outlined.Delete,
                title = "Clear Chat History",
                subtitle = "Delete all conversations",
                onClick = { viewModel.showClearDataDialog() },
                titleColor = ErrorColor
            )
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            
            // About section
            SettingsSectionHeader(title = "About")
            
            SettingsItem(
                icon = Icons.Outlined.Info,
                title = "About Xirea",
                subtitle = "Version, credits & more",
                onClick = onNavigateToAbout,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Clear data confirmation dialog
        if (uiState.showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideClearDataDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = ErrorColor
                    )
                },
                title = { Text("Clear Chat History") },
                text = { 
                    Text("Are you sure you want to delete all ${uiState.chatCount} conversation${if (uiState.chatCount != 1) "s" else ""}? This action cannot be undone.") 
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearAllChatHistory() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = ErrorColor
                        )
                    ) {
                        Text("Delete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideClearDataDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        trailing?.invoke()
    }
}
