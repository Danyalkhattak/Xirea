package com.dannyk.xirea.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dannyk.xirea.data.model.AIModel
import com.dannyk.xirea.ui.theme.*
import com.dannyk.xirea.util.StorageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // File picker for importing local models
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importLocalModel(it) }
    }
    
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "AI Models",
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device RAM info card
            item {
                InfoCard(
                    icon = Icons.Outlined.Memory,
                    title = "Device RAM: ${uiState.deviceTotalRamMB / 1024}GB",
                    subtitle = "Models marked \"Best\" are recommended for your device",
                    iconTint = MaterialTheme.colorScheme.primary,
                    bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            }
            
            // Storage info card
            item {
                InfoCard(
                    icon = Icons.Default.Storage,
                    title = "Storage: ${StorageUtils.formatFileSize(uiState.totalStorageUsed)} used",
                    subtitle = "${StorageUtils.formatFileSize(uiState.availableStorage)} available",
                    iconTint = if (uiState.isLowStorage) WarningColor else MaterialTheme.colorScheme.secondary,
                    bgColor = if (uiState.isLowStorage) WarningColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    warningText = if (uiState.isLowStorage) "Low storage. Delete unused models." else null
                )
            }
            
            // Load from Storage button
            item {
                OutlinedButton(
                    onClick = { pickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Load Model from Storage",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Section header
            item {
                Text(
                    text = "Available Models",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                )
            }
            
            items(uiState.models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    isSelected = uiState.selectedModelId == model.id,
                    isLoading = uiState.loadingModelId == model.id,
                    loadingProgress = if (uiState.loadingModelId == model.id) uiState.loadingProgress else 0,
                    isDownloading = uiState.downloadingModelId == model.id,
                    downloadProgress = if (uiState.downloadingModelId == model.id) uiState.downloadProgress else 0,
                    downloadedBytes = if (uiState.downloadingModelId == model.id) uiState.downloadedBytes else 0,
                    totalBytes = if (uiState.downloadingModelId == model.id) uiState.totalBytes else 0,
                    deviceTotalRamMB = uiState.deviceTotalRamMB,
                    onSelect = { viewModel.selectModel(model) },
                    onDownload = { viewModel.downloadModel(model) },
                    onDelete = { viewModel.showDeleteDialog(model) },
                    onCancelDownload = { viewModel.cancelDownload() }
                )
            }
            
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        
        // Delete confirmation dialog
        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideDeleteDialog() },
                icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ErrorColor) },
                title = { Text("Delete Model") },
                text = { 
                    Text("Are you sure you want to delete ${uiState.modelToDelete?.name}? You'll need to download it again to use it.") 
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteModel() },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorColor)
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteDialog() }) { Text("Cancel") }
                }
            )
        }
        
        // Import progress dialog
        if (uiState.isImporting) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Importing Model") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { uiState.importProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${uiState.importProgress}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Copying model to app storage...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = { }
            )
        }
    }
}

@Composable
fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    warningText: String? = null
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (warningText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = warningText, style = MaterialTheme.typography.bodySmall, color = WarningColor)
                }
            }
        }
    }
}

private data class RamRecommendation(
    val label: String,
    val description: String,
    val color: androidx.compose.ui.graphics.Color,
    val isRecommended: Boolean
)

private fun getRamRecommendation(modelFileSizeMB: Long, deviceTotalRamMB: Long): RamRecommendation {
    val estimatedRamNeededMB = (modelFileSizeMB * 1.2).toLong()
    val availableForModel = deviceTotalRamMB - 1500
    
    return when {
        availableForModel >= estimatedRamNeededMB * 2 -> RamRecommendation(
            label = "Best for your device",
            description = "Runs smoothly with your ${deviceTotalRamMB / 1024}GB RAM",
            color = SuccessColor,
            isRecommended = true
        )
        availableForModel >= estimatedRamNeededMB -> RamRecommendation(
            label = "Compatible",
            description = "Should work well on your device",
            color = InfoColor,
            isRecommended = false
        )
        availableForModel >= estimatedRamNeededMB * 0.7 -> RamRecommendation(
            label = "May be slow",
            description = "Your device may struggle with this model",
            color = WarningColor,
            isRecommended = false
        )
        else -> RamRecommendation(
            label = "Not recommended",
            description = "Needs more RAM than available",
            color = ErrorColor,
            isRecommended = false
        )
    }
}

@Composable
fun ModelCard(
    model: AIModel,
    isSelected: Boolean,
    isLoading: Boolean,
    loadingProgress: Int = 0,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    deviceTotalRamMB: Long = 4096,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress / 100f,
        label = "download_progress"
    )
    
    val modelSizeMB = model.fileSize / (1024 * 1024)
    val ramRecommendation = getRamRecommendation(modelSizeMB, deviceTotalRamMB)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = model.isDownloaded && !isLoading) { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SuccessColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = StorageUtils.formatFileSize(model.fileSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "  |  ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "v${model.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (model.isDownloaded) {
                            Text(
                                text = "  |  ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (model.isLocalModel) "Imported" else "Ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (model.isLocalModel) InfoColor else SuccessColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ramRecommendation.color.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (ramRecommendation.isRecommended) Icons.Default.ThumbUp else Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = ramRecommendation.color,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = ramRecommendation.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = ramRecommendation.color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Download progress bar
            AnimatedVisibility(visible = isDownloading) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Downloading...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            if (totalBytes > 0) {
                                Text(
                                    "${StorageUtils.formatFileSize(downloadedBytes)} / ${StorageUtils.formatFileSize(totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$downloadProgress%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TextButton(
                                onClick = onCancelDownload,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            
            // Loading indicator with progress
            AnimatedVisibility(visible = isLoading) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading model...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            "$loadingProgress%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            
            // Action buttons
            if (!isDownloading && !isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (model.isDownloaded) {
                        if (!isSelected) {
                            Button(
                                onClick = onSelect,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}