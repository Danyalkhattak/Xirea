package com.dannyk.xirea.ui.models

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dannyk.xirea.data.model.AIModel
import com.dannyk.xirea.ui.theme.ErrorColor
import com.dannyk.xirea.ui.theme.InfoColor
import com.dannyk.xirea.ui.theme.SuccessColor
import com.dannyk.xirea.ui.theme.WarningColor
import com.dannyk.xirea.util.StorageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error as snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Models") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device RAM info card
            item {
                DeviceRamInfoCard(
                    deviceTotalRamMB = uiState.deviceTotalRamMB
                )
            }
            
            // Storage info card
            item {
                StorageInfoCard(
                    totalUsed = uiState.totalStorageUsed,
                    availableStorage = uiState.availableStorage,
                    isLowStorage = uiState.isLowStorage
                )
            }
            
            item {
                Text(
                    text = "Available Models",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(uiState.models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    isSelected = uiState.selectedModelId == model.id,
                    isLoading = uiState.loadingModelId == model.id,
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
                title = { Text("Delete Model") },
                text = { 
                    Text("Are you sure you want to delete ${uiState.modelToDelete?.name}? You'll need to download it again to use it.") 
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteModel() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun StorageInfoCard(
    totalUsed: Long,
    availableStorage: Long,
    isLowStorage: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLowStorage) {
                WarningColor.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = if (isLowStorage) WarningColor else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Storage",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${StorageUtils.formatFileSize(totalUsed)} used • ${StorageUtils.formatFileSize(availableStorage)} available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isLowStorage) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Low storage space. Delete unused models to free up space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningColor
                    )
                }
            }
        }
    }
}

/**
 * Determines the RAM recommendation level for a model based on device RAM.
 * Returns a Triple of (label, color, icon).
 */
private data class RamRecommendation(
    val label: String,
    val description: String,
    val color: androidx.compose.ui.graphics.Color,
    val isRecommended: Boolean
)

private fun getRamRecommendation(modelFileSizeMB: Long, deviceTotalRamMB: Long): RamRecommendation {
    // Models need roughly 1.2x their file size in RAM when loaded
    val estimatedRamNeededMB = (modelFileSizeMB * 1.2).toLong()
    val availableForModel = deviceTotalRamMB - 1500 // Reserve ~1.5GB for OS + app
    
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
fun DeviceRamInfoCard(
    deviceTotalRamMB: Long
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Device RAM: ${deviceTotalRamMB / 1024}GB",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Models marked \"Best for your device\" are recommended",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: AIModel,
    isSelected: Boolean,
    isLoading: Boolean,
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
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Model icon
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SuccessColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = StorageUtils.formatFileSize(model.fileSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "v${model.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (model.isDownloaded) {
                            Text(
                                text = " • ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessColor
                            )
                        }
                    }
                    
                    // RAM Recommendation Badge
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ramRecommendation.color.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (ramRecommendation.isRecommended) Icons.Default.ThumbUp else Icons.Outlined.Memory,
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
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Downloading... $downloadProgress%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (totalBytes > 0) {
                                Text(
                                    text = "${StorageUtils.formatFileSize(downloadedBytes)} / ${StorageUtils.formatFileSize(totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = onCancelDownload,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
            
            // Loading indicator
            AnimatedVisibility(visible = isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading model...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Action buttons
            if (!isDownloading && !isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (model.isDownloaded) {
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                        
                        if (!isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = onSelect) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load")
                            }
                        }
                    } else {
                        Button(onClick = onDownload) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}
