package com.example.utils

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CacheManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var INSTANCE: CacheManager? = null
        fun getInstance(c: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: CacheManager(c.applicationContext).also { INSTANCE = it }
        }
        // Size limits in MB
        const val IMG_MB = 250
        const val PREV_MB = 500
        const val THUMB_MB = 100
        const val DOC_MB = 200
        const val INDEX_MB = 50
        
        const val TOTAL_CACHE_MB = 2048 // Auto-trigger cleanup at this
    }
    
    // Coil image cache with aggressive config
    fun setupCoil() {
        val imageLoader = ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.25).build() }
            .diskCache { 
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("img_cache"))
                    .maxSizeBytes(IMG_MB * 1024L * 1024L)
                    .build() 
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
    }
    
    // LRU thumbnail cache (memory only)
    private val thumbCache = LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())
    
    // Disk cache directories
    private val previewDir = File(context.cacheDir, "previews").apply { mkdirs() }
    private val docDir = File(context.cacheDir, "docs").apply { mkdirs() }
    private val tempDir = File(context.cacheDir, "temp").apply { mkdirs() }
    private val indexFile = File(context.cacheDir, "search.idx")
    
    // === PUT / GET operations ===
    fun putThumb(path: String, bmp: Bitmap) { thumbCache.put(path, bmp) }
    fun getThumb(path: String): Bitmap? = thumbCache.get(path)
    
    fun putPreview(path: String, data: ByteArray) {
        File(previewDir, path.hashCode().toString()).writeBytes(data)
        checkAutoCleanup()
    }
    fun getPreview(path: String): ByteArray? = File(previewDir, path.hashCode().toString()).let { if (it.exists()) it.readBytes() else null }
    
    fun putDoc(id: String, html: String) {
        File(docDir, id).writeText(html)
        checkAutoCleanup()
    }
    fun getDoc(id: String): String? = File(docDir, id).let { if (it.exists()) it.readText() else null }
    
    fun putTemp(name: String, data: ByteArray) = File(tempDir, name).writeBytes(data)
    fun getTemp(name: String): ByteArray? = File(tempDir, name).let { if (it.exists()) it.readBytes() else null }
    fun clearTemp() = tempDir.listFiles()?.forEach { it.delete() }
    
    fun putIndex(list: List<String>) = indexFile.writeText(Json.encodeToString(list))
    fun getIndex(): List<String> = if (indexFile.exists()) Json.decodeFromString(indexFile.readText()) else emptyList()
    
    // === SMART AUTO-CLEANUP ===
    private fun checkAutoCleanup() {
        val currentSize = getTotalSizeBytes()
        val limitBytes = TOTAL_CACHE_MB * 1024L * 1024L
        if (currentSize > limitBytes * 0.8) { // 80% threshold
            CoroutineScope(Dispatchers.IO).launch { smartCleanup() }
        }
    }
    
    suspend fun smartCleanup() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val threeDays = 3 * oneDay
        val oneWeek = 7 * oneDay
        
        // Priority 1: Delete temp files (always safe)
        tempDir.listFiles()?.forEach { it.delete() }
        
        // Priority 2: Delete old previews (>3 days)
        previewDir.listFiles()?.forEach { 
            if (now - it.lastModified() > threeDays) it.delete() 
        }
        
        // Priority 3: Delete old documents (>7 days)
        docDir.listFiles()?.forEach { 
            if (now - it.lastModified() > oneWeek) it.delete() 
        }
        
        // Priority 4: If still over limit, delete LRU from previews
        if (getTotalSizeBytes() > TOTAL_CACHE_MB * 1024L * 1024L) {
            val previews = previewDir.listFiles()?.sortedBy { it.lastModified() }
            var deleted = 0L
            previews?.forEach { file ->
                if (getTotalSizeBytes() - deleted < TOTAL_CACHE_MB * 1024L * 1024L * 0.6) return@forEach
                deleted += file.length()
                file.delete()
            }
        }
        
        // Priority 5: Clear thumbnail memory cache
        thumbCache.evictAll()
        
        // Priority 6: Trim Coil disk cache
        Coil.imageLoader(context).diskCache?.clear()
    }
    
    // === MEMORY PRESSURE ===
    fun onLowMemory() {
        thumbCache.evictAll()
        clearTemp()
        System.gc()
    }
    
    fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                thumbCache.evictAll()
                clearTemp()
                CoroutineScope(Dispatchers.IO).launch { smartCleanup() }
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                thumbCache.trimToSize(thumbCache.size() / 2)
            }
        }
    }
    
    // === SIZE CALCULATIONS ===
    fun getTotalSizeBytes(): Long = previewDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() +
                                     docDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() +
                                     tempDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() +
                                     (if (indexFile.exists()) indexFile.length() else 0)
    
    fun getTotalSize(): String = formatSize(getTotalSizeBytes())
    
    fun getBreakdown(): Map<String, String> = mapOf(
        "Images" to formatSize(context.cacheDir.resolve("img_cache").walkTopDown().filter { it.isFile }.map { it.length() }.sum()),
        "Previews" to formatSize(previewDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()),
        "Documents" to formatSize(docDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()),
        "Temp" to formatSize(tempDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()),
        "Index" to formatSize(if (indexFile.exists()) indexFile.length() else 0)
    )
    
    // === NUCLEAR OPTION ===
    suspend fun nuke() = withContext(Dispatchers.IO) {
        previewDir.deleteRecursively(); docDir.deleteRecursively(); tempDir.deleteRecursively()
        thumbCache.evictAll(); indexFile.delete()
        context.cacheDir.resolve("img_cache").deleteRecursively()
        previewDir.mkdirs(); docDir.mkdirs(); tempDir.mkdirs()
    }
    
    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
