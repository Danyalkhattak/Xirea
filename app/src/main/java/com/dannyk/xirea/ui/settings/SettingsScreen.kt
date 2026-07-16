package com.dannyk.xirea.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dannyk.xirea.ui.theme.ErrorColor
import com.dannyk.xirea.util.StorageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToCrashReport: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.refreshModelStatus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Appearance Section
            SettingsSectionHeader(title = "Appearance")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
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
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // AI Model Section
            SettingsSectionHeader(title = "AI Model")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsItem(
                    icon = Icons.Outlined.Memory,
                    title = "Selected Model",
                    subtitle = uiState.selectedModelName ?: "No model selected",
                    onClick = onNavigateToModels,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Storage Section
            SettingsSectionHeader(title = "Storage")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsItem(
                    icon = Icons.Outlined.Folder,
                    title = "Models Storage",
                    subtitle = StorageUtils.formatFileSize(uiState.modelsStorageSize),
                    onClick = onNavigateToModels
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsItem(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "Chat History",
                    subtitle = "${uiState.chatCount} conversation${if (uiState.chatCount != 1) "s" else ""}"
                )
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsItem(
                    icon = Icons.Outlined.Delete,
                    title = "Clear Chat History",
                    subtitle = "Delete all conversations",
                    onClick = { viewModel.showClearDataDialog() },
                    titleColor = ErrorColor
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // About Section
            SettingsSectionHeader(title = "About")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsItem(
                    icon = Icons.Outlined.Report,
                    title = "Report a Problem",
                    subtitle = "Send a crash report by email",
                    onClick = onNavigateToCrashReport,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "About Xirea",
                    subtitle = "Version, credits & more",
                    onClick = onNavigateToAbout,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
        
        if (uiState.showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideClearDataDialog() },
                icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ErrorColor) },
                title = { Text("Clear Chat History") },
                text = { 
                    Text("Are you sure you want to delete all ${uiState.chatCount} conversation${if (uiState.chatCount != 1) "s" else ""}? This cannot be undone.") 
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearAllChatHistory() },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorColor)
                    ) { Text("Delete All") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideClearDataDialog() }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = FontWeight.Medium
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