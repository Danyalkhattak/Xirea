package com.dannyk.xirea.util

import java.util.Locale

object StorageUtils {
    
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        
        return String.format(
            Locale.getDefault(),
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }
    
    fun formatStoragePercentage(used: Long, total: Long): String {
        if (total <= 0) return "0%"
        val percentage = (used.toDouble() / total.toDouble() * 100).toInt()
        return "$percentage%"
    }
    
    fun bytesToMB(bytes: Long): Double {
        return bytes / (1024.0 * 1024.0)
    }
    
    fun bytesToGB(bytes: Long): Double {
        return bytes / (1024.0 * 1024.0 * 1024.0)
    }
    
    fun isLowStorage(availableBytes: Long): Boolean {
        // Consider low storage if less than 500MB available
        return availableBytes < 500 * 1024 * 1024
    }
}
