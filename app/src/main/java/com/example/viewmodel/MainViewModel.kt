package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.RecentFileEntity
import com.example.data.SettingEntity
import com.example.services.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.InputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = FileManager()
    private val repository: AppRepository

    // Indexing for super-fast search
    private val allIndexedFiles = java.util.Collections.synchronizedList(mutableListOf<String>())
    
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    private val _searchTermsHistory = MutableStateFlow<List<String>>(emptyList())
    val searchTermsHistory: StateFlow<List<String>> = _searchTermsHistory.asStateFlow()

    private fun loadSearchTermsHistory() {
        val prefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val historyStr = prefs.getString("search_terms_history", "") ?: ""
        if (historyStr.isNotEmpty()) {
            _searchTermsHistory.value = historyStr.split("|*|")
        }
    }

    fun addSearchTermToHistory(term: String) {
        if (term.trim().isEmpty()) return
        val current = _searchTermsHistory.value.toMutableList()
        current.remove(term.trim()) // Remove if exists to bring to front
        current.add(0, term.trim()) // Add to front
        if (current.size > 15) {
            current.removeAt(current.size - 1)
        }
        _searchTermsHistory.value = current
        val prefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("search_terms_history", current.joinToString("|*|")).apply()
    }

    fun removeSearchTermFromHistory(term: String) {
        val current = _searchTermsHistory.value.toMutableList()
        current.remove(term)
        _searchTermsHistory.value = current
        val prefs = getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("search_terms_history", current.joinToString("|*|")).apply()
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.recentFileDao(), database.settingDao())
        
        loadSearchTermsHistory()

        
        // Initialize settings, clean up sample files, and build search index from stored cache and background scanner
        viewModelScope.launch(Dispatchers.IO) {
            // Fix Settings DB initialization to inspect real Dao row
            val dbSettings = database.settingDao().getSettingsDirect()
            if (dbSettings == null) {
                repository.updateSettings(SettingEntity())
            }

            // Delete previously created sample files from database to ensure fresh start
            repository.deleteSampleFiles()

            // 1. Immediately load previously saved index from device files cache if any
            try {
                val cacheFile = File(getApplication<Application>().cacheDir, "device_files_cache.txt")
                if (cacheFile.exists()) {
                    val lines = cacheFile.readLines()
                    allIndexedFiles.addAll(lines)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun buildStorageIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isIndexing.value) return@launch
            _isIndexing.value = true
            try {
                val rootDir = File("/storage/emulated/0")
                val appFilesDir = getApplication<Application>().filesDir
                val externalFilesDir = getApplication<Application>().getExternalFilesDir(null)
                val freshList = mutableListOf<String>()

                val dirsToScan = mutableListOf<File>()
                if (rootDir.exists()) {
                    dirsToScan.add(rootDir)
                    val defaultDirs = listOf("Download", "Documents", "DCIM", "Pictures", "Music")
                    for (dirName in defaultDirs) {
                        val sub = File(rootDir, dirName)
                        if (sub.exists() && sub.isDirectory) {
                            dirsToScan.add(sub)
                        }
                    }
                }
                if (appFilesDir.exists()) {
                    dirsToScan.add(appFilesDir)
                }
                if (externalFilesDir != null && externalFilesDir.exists()) {
                    dirsToScan.add(externalFilesDir)
                }

                for (dir in dirsToScan.distinctBy { it.absolutePath }) {
                    if (dir.exists()) {
                        dir.walkTopDown()
                            .maxDepth(3)
                            .onEnter { d ->
                                val name = d.name
                                !name.startsWith(".") && 
                                !name.equals("Android", ignoreCase = true) &&
                                !name.contains("cache", ignoreCase = true) &&
                                !name.contains("tmp", ignoreCase = true) &&
                                !name.contains("com.", ignoreCase = true)
                            }
                            .filter { it.isFile }
                            .take(2000)
                            .forEach {
                                if (freshList.size < 5000) {
                                    freshList.add(it.absolutePath)
                                }
                            }
                    }
                }

                val uniqueList = freshList.distinct()

                synchronized(allIndexedFiles) {
                    allIndexedFiles.clear()
                    allIndexedFiles.addAll(uniqueList)
                }

                // Persist the updated flat files cache
                try {
                    val cacheFile = File(getApplication<Application>().cacheDir, "device_files_cache.txt")
                    cacheFile.writeText(uniqueList.joinToString("\n"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isIndexing.value = false
            }
        }
    }

    // Call this whenever files are modified, saved, or imported, to dynamically update search results!
    private fun indexFile(filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(allIndexedFiles) {
                if (!allIndexedFiles.contains(filePath)) {
                    allIndexedFiles.add(filePath)
                    // Persist to cache file in background
                    try {
                        val cacheFile = File(getApplication<Application>().cacheDir, "device_files_cache.txt")
                        cacheFile.writeText(allIndexedFiles.joinToString("\n"))
                    } catch (e: java.io.IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // Settings State
    val settingsState: StateFlow<SettingEntity> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingEntity()
    )

    // Recent files state combined with search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    val recentFilesState: StateFlow<List<RecentFileEntity>> = repository.allRecentFiles
        .combine(_searchQuery) { files, query ->
            _isInitialized.value = true
            if (query.isEmpty()) {
                files
            } else {
                files.filter { it.name.contains(query, ignoreCase = true) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current open file states
    sealed class FileContentState {
        object Idle : FileContentState()
        object Loading : FileContentState()
        data class TextSuccess(val content: String, val file: RecentFileEntity) : FileContentState()
        data class CsvSuccess(val rows: List<List<String>>, val file: RecentFileEntity) : FileContentState()
        data class ZipSuccess(val root: FileManager.ZipNode, val file: RecentFileEntity, val password: String = "") : FileContentState()
        data class ArchivePasswordRequired(val file: RecentFileEntity) : FileContentState()
        data class ImageSuccess(val file: RecentFileEntity) : FileContentState()
        data class PdfSuccess(val file: RecentFileEntity) : FileContentState()
        data class DocxSuccess(val base64Data: String, val file: RecentFileEntity) : FileContentState()
        data class MediaSuccess(val file: RecentFileEntity, val isAudio: Boolean) : FileContentState()
        data class BinarySuccess(val hexRows: List<String>, val asciiRows: List<String>, val file: RecentFileEntity) : FileContentState()
        data class Error(val message: String) : FileContentState()
    }

    private val _currentFileState = MutableStateFlow<FileContentState>(FileContentState.Idle)
    val currentFileState: StateFlow<FileContentState> = _currentFileState.asStateFlow()

    private val _expandedZipPaths = MutableStateFlow<Set<String>>(emptySet())
    val expandedZipPaths: StateFlow<Set<String>> = _expandedZipPaths.asStateFlow()

    fun toggleZipPathExpanded(path: String) {
        val current = _expandedZipPaths.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _expandedZipPaths.value = current
    }

    fun expandZipPath(path: String) {
        val current = _expandedZipPaths.value.toMutableSet()
        current.add(path)
        _expandedZipPaths.value = current
    }

    private val _loadingFilePath = MutableStateFlow<String?>(null)
    val loadingFilePath: StateFlow<String?> = _loadingFilePath.asStateFlow()

    // Navigation trigger event (prevents race conditions)
    sealed class NavigationEvent {
        data class NavigateToPreview(val fileState: FileContentState) : NavigationEvent()
        data class ShowError(val message: String) : NavigationEvent()
    }

    private val _navigationEvent = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    // File operation events (e.g. notifications)
    private val _fileEvent = MutableStateFlow<String?>(null)
    val fileEvent = _fileEvent.asStateFlow()

    fun clearFileEvent() {
        _fileEvent.value = null
    }

    fun showNotification(message: String) {
        _fileEvent.value = message
    }

    fun resetFileState() {
        _currentFileState.value = FileContentState.Idle
    }

    private fun isBinaryFile(file: File): Boolean {
        if (!file.exists()) return false
        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(1024)
                val bytesRead = fis.read(buffer)
                if (bytesRead <= 0) return false
                for (i in 0 until bytesRead) {
                    if (buffer[i] == 0.toByte()) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // default to text if unreadable/error
        }
        return false
    }

    private fun generateHexDump(file: File): Pair<List<String>, List<String>> {
        val hexLines = mutableListOf<String>()
        val asciiLines = mutableListOf<String>()
        val maxBytesToDump = 4096 // Limit dump size for high UI performance and quick display
        try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(16)
                var offset = 0
                while (offset < maxBytesToDump) {
                    val bytesRead = fis.read(buffer)
                    if (bytesRead <= 0) break
                    
                    val hexBuilder = StringBuilder()
                    val asciiBuilder = StringBuilder()
                    
                    // AddOffset address
                    hexBuilder.append(String.format("%08X: ", offset))
                    
                    for (i in 0 until 16) {
                        if (i < bytesRead) {
                            val b = buffer[i].toInt() and 0xFF
                            hexBuilder.append(String.format("%02X ", b))
                            val c = b.toChar()
                            if (c in ' '..'~') {
                                asciiBuilder.append(c)
                            } else {
                                asciiBuilder.append('.')
                            }
                        } else {
                            hexBuilder.append("   ")
                        }
                        if (i == 7) {
                            hexBuilder.append(" ")
                        }
                    }
                    hexLines.add(hexBuilder.toString())
                    asciiLines.add(asciiBuilder.toString())
                    offset += bytesRead
                }
            }
        } catch (e: Exception) {
            hexLines.add("Error generating hex dump")
            asciiLines.add("")
        }
        return Pair(hexLines, asciiLines)
    }

    private fun copyToTempCache(file: File): File {
        return try {
            // For large files (>30MB), skip caching entirely to save time and space
            if (file.length() > 30 * 1024 * 1024) {
                return file
            }

            val cacheDir = getApplication<Application>().cacheDir
            val tempDir = File(cacheDir, "file_cache")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            val tempFile = File(tempDir, file.name)
            // Check if already cached properly
            if (tempFile.exists() && tempFile.length() == file.length() && tempFile.lastModified() == file.lastModified()) {
                return tempFile
            }
            
            // Manage cache size: keep only last 5 files
            val existingFiles = tempDir.listFiles()?.sortedBy { it.lastModified() }
            if (existingFiles != null && existingFiles.size >= 5) {
                existingFiles.take(existingFiles.size - 4).forEach { it.delete() }
            }

            file.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.setLastModified(file.lastModified())
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            file
        }
    }

    fun openFile(fileEntity: RecentFileEntity, archivePassword: String = "") {
        _currentFileState.value = FileContentState.Loading
        _loadingFilePath.value = fileEntity.path
        
        viewModelScope.launch {
            _navigationEvent.send(NavigationEvent.NavigateToPreview(FileContentState.Loading))
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update last opened timestamp in database and retrieve stable DB ID
                val existing = repository.getRecentFileByPath(fileEntity.path)
                val updatedEntity = if (existing != null) {
                    fileEntity.copy(
                        id = existing.id,
                        lastOpened = System.currentTimeMillis()
                    )
                } else {
                    fileEntity.copy(lastOpened = System.currentTimeMillis())
                }
                val savedId = repository.insertRecentFile(updatedEntity)
                val finalEntity = updatedEntity.copy(id = if (existing != null) existing.id else savedId.toInt())
                
                // Add this path to search index dynamically if missing
                indexFile(fileEntity.path)

                // Save this file path to shared preferences as 'last_previewed_file_path'
                val prefs = getApplication<android.app.Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("last_previewed_file_path", fileEntity.path).apply()
 
                val file = File(fileEntity.path)
                if (!file.exists()) {
                    _currentFileState.value = FileContentState.Error("File not found on disk. It might have been deleted.")
                    _navigationEvent.send(NavigationEvent.NavigateToPreview(_currentFileState.value))
                    return@launch
                }
 
                val tempCachedFile = copyToTempCache(file)
                val activePath = tempCachedFile.absolutePath
                val tempEntity = finalEntity.copy(path = activePath)
 
                val encoding = settingsState.value.defaultEncoding
                val ext = fileEntity.extension.lowercase()
                
                val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico")
                val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "mid", "midi")
                val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "3gp")
                
                when {
                    ext == "docx" -> {
                        val fileBytes = File(activePath).readBytes()
                        val base64 = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
                        _currentFileState.value = FileContentState.DocxSuccess(base64, finalEntity)
                    }
                    ext == "pptx" || ext == "ppt" -> {
                        val text = fileManager.readPptxText(activePath)
                        _currentFileState.value = FileContentState.TextSuccess(text, finalEntity)
                    }
                    ext == "pdf" -> {
                        _currentFileState.value = FileContentState.PdfSuccess(finalEntity)
                    }
                    ext == "csv" || ext == "tsv" -> {
                        val csvData = fileManager.parseCsv(activePath)
                        _currentFileState.value = FileContentState.CsvSuccess(csvData, finalEntity)
                    }
                    ext == "xlsx" || ext == "xls" -> {
                        val excelData = fileManager.parseExcel(activePath)
                        _currentFileState.value = FileContentState.CsvSuccess(excelData, finalEntity)
                    }
                    ext == "zip" || ext == "7z" || ext == "rar" || ext == "tar" || ext == "gz" || ext == "tgz" -> {
                        if (archivePassword.isEmpty() && fileManager.isArchiveEncrypted(activePath)) {
                            _currentFileState.value = FileContentState.ArchivePasswordRequired(finalEntity)
                        } else {
                            val zipStructure = fileManager.parseZipStructure(activePath, if (archivePassword.isNotEmpty()) archivePassword else null)
                            _currentFileState.value = FileContentState.ZipSuccess(zipStructure, finalEntity, archivePassword)
                        }
                    }
                    imageExtensions.contains(ext) -> {
                        _currentFileState.value = FileContentState.ImageSuccess(tempEntity)
                    }
                    audioExtensions.contains(ext) -> {
                        _currentFileState.value = FileContentState.MediaSuccess(tempEntity, isAudio = true)
                    }
                    videoExtensions.contains(ext) -> {
                        _currentFileState.value = FileContentState.MediaSuccess(tempEntity, isAudio = false)
                    }
                    isBinaryFile(tempCachedFile) -> {
                        val (hexRows, asciiRows) = generateHexDump(tempCachedFile)
                        _currentFileState.value = FileContentState.BinarySuccess(hexRows, asciiRows, finalEntity)
                    }
                    else -> {
                        // Standard text/md/code file
                        val text = fileManager.readFileContent(activePath, encoding)
                        _currentFileState.value = FileContentState.TextSuccess(text, finalEntity)
                    }
                }
            } catch (e: Exception) {
                _currentFileState.value = FileContentState.Error("Failed to open file: ${e.message}")
            } finally {
                _loadingFilePath.value = null
                _navigationEvent.send(NavigationEvent.NavigateToPreview(_currentFileState.value))
            }
        }
    }

    fun openZipEntry(parentZipEntity: RecentFileEntity, node: FileManager.ZipNode, password: String = "") {
        _currentFileState.value = FileContentState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val tempZipExtractsDir = File(context.cacheDir, "zip_extracts")
                if (!tempZipExtractsDir.exists()) {
                    tempZipExtractsDir.mkdirs()
                }
                
                val uniqueSubName = "${parentZipEntity.id}_${node.path.replace("/", "_")}"
                val ext = node.name.substringAfterLast('.', "").lowercase()
                val localFile = File(tempZipExtractsDir, uniqueSubName)
                
                val pwdParam = if (password.isNotEmpty()) password else null
                val extracted = fileManager.extractZipEntry(parentZipEntity.path, node.path, localFile, pwdParam)
                if (extracted && localFile.exists()) {
                    val zipEntryEntity = RecentFileEntity(
                        path = localFile.absolutePath,
                        name = "${parentZipEntity.name} > ${node.name}",
                        size = localFile.length(),
                        extension = ext,
                        lastOpened = System.currentTimeMillis(),
                        isSample = false,
                        parentZipPath = parentZipEntity.path,
                        zipEntryPath = node.path
                    )
                    
                    val insertedId = repository.insertRecentFile(zipEntryEntity)
                    val savedEntity = zipEntryEntity.copy(id = insertedId.toInt())
                    
                    openFile(savedEntity)
                } else {
                    _currentFileState.value = FileContentState.Error("Could not extract entry: ${node.name}")
                    _navigationEvent.send(NavigationEvent.NavigateToPreview(_currentFileState.value))
                }
            } catch (e: Exception) {
                _currentFileState.value = FileContentState.Error("Failed to open ZIP entry: ${e.message}")
                _navigationEvent.send(NavigationEvent.NavigateToPreview(_currentFileState.value))
            }
        }
    }

    fun openParentZip(parentPath: String) {
        _currentFileState.value = FileContentState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parentEntity = repository.getRecentFileByPath(parentPath)
                if (parentEntity != null) {
                    openFile(parentEntity)
                } else {
                    val file = File(parentPath)
                    if (file.exists()) {
                        val recentFile = RecentFileEntity(
                            path = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            extension = file.name.substringAfterLast('.', "").lowercase(),
                            lastOpened = System.currentTimeMillis(),
                            isSample = false
                        )
                        val id = repository.insertRecentFile(recentFile)
                        openFile(recentFile.copy(id = id.toInt()))
                    } else {
                        _currentFileState.value = FileContentState.Error("Parent ZIP file not found on disk.")
                        _navigationEvent.send(NavigationEvent.NavigateToPreview(_currentFileState.value))
                    }
                }
            } catch (e: Exception) {
                _currentFileState.value = FileContentState.Error("Failed to reopen parent ZIP: ${e.message}")
                _navigationEvent.send(NavigationEvent.NavigateToPreview(_currentFileState.value))
            }
        }
    }

    fun saveSpreadsheetFile(fileEntity: RecentFileEntity, newRows: List<List<String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ext = fileEntity.extension.lowercase()
                if (ext == "csv" || ext == "tsv") {
                    fileManager.saveCsv(fileEntity.path, newRows)
                } else if (ext == "xlsx" || ext == "xls") {
                    fileManager.saveExcel(fileEntity.path, newRows)
                }
                
                // Update size in database
                val diskFile = File(fileEntity.path)
                val updatedEntity = fileEntity.copy(
                    size = diskFile.length(),
                    lastOpened = System.currentTimeMillis()
                )
                
                // CRITICAL ZIP BACK-SYNC
                if (fileEntity.parentZipPath != null && fileEntity.zipEntryPath != null) {
                    val updatedZip = fileManager.updateZipEntry(
                        zipFilePath = fileEntity.parentZipPath,
                        entryPath = fileEntity.zipEntryPath,
                        entrySrcFile = diskFile
                    )
                    
                    if (updatedZip) {
                        try {
                            val parentZipFile = File(fileEntity.parentZipPath)
                            val parentEntity = repository.getRecentFileByPath(fileEntity.parentZipPath)
                            parentEntity?.let {
                                repository.insertRecentFile(it.copy(
                                    size = parentZipFile.length(),
                                    lastOpened = System.currentTimeMillis()
                                ))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                // Update UI state
                repository.insertRecentFile(updatedEntity)
                withContext(Dispatchers.Main) {
                    _currentFileState.value = FileContentState.CsvSuccess(newRows, updatedEntity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveTextFile(fileEntity: RecentFileEntity, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ext = fileEntity.extension.lowercase()
                if (ext == "docx") {
                    fileManager.writeDocxText(fileEntity.path, newContent)
                } else if (ext == "pptx" || ext == "ppt") {
                    fileManager.writePptxText(fileEntity.path, newContent)
                } else {
                    val encoding = settingsState.value.defaultEncoding
                    fileManager.writeFileContent(fileEntity.path, newContent, encoding)
                }
                
                // Update size in database
                val diskFile = File(fileEntity.path)
                val updatedEntity = fileEntity.copy(
                    size = diskFile.length(),
                    lastOpened = System.currentTimeMillis()
                )
                
                // CRITICAL ZIP BACK-SYNC: If this file belongs to a ZIP archive, write those updates back into the parent ZIP container!
                if (fileEntity.parentZipPath != null && fileEntity.zipEntryPath != null) {
                    val updatedZip = fileManager.updateZipEntry(
                        zipFilePath = fileEntity.parentZipPath,
                        entryPath = fileEntity.zipEntryPath,
                        entrySrcFile = diskFile
                    )
                    
                    if (updatedZip) {
                        try {
                            val parentZipFile = File(fileEntity.parentZipPath)
                            val parentEntity = repository.getRecentFileByPath(fileEntity.parentZipPath)
                            parentEntity?.let {
                                repository.insertRecentFile(it.copy(
                                    size = parentZipFile.length(),
                                    lastOpened = System.currentTimeMillis()
                                ))
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }

                repository.insertRecentFile(updatedEntity)
                
                // Index the saved file path dynamically
                indexFile(fileEntity.path)
                
                // If it was the active file state, update its content so the preview / editor gets the refresh!
                val currentState = _currentFileState.value
                if (currentState is FileContentState.TextSuccess && currentState.file.path == fileEntity.path) {
                    _currentFileState.value = FileContentState.TextSuccess(newContent, updatedEntity)
                }

                if (settingsState.value.notificationSaveComplete) {
                    _fileEvent.value = "Saved successfully!"
                }
            } catch (e: Exception) {
                _fileEvent.value = "Error saving file: ${e.message}"
            }
        }
    }

    fun deleteRecentFile(fileEntity: RecentFileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeRecentFileById(fileEntity.id)
            // If the deleted file is currently open, reset to Idle
            val currentState = _currentFileState.value
            if (currentState is FileContentState.TextSuccess && currentState.file.id == fileEntity.id ||
                currentState is FileContentState.CsvSuccess && currentState.file.id == fileEntity.id ||
                currentState is FileContentState.ZipSuccess && currentState.file.id == fileEntity.id) {
                _currentFileState.value = FileContentState.Idle
            }
        }
    }

    fun clearRecentFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _currentFileState.value = FileContentState.Idle
        }
    }

    fun clearEditorCache() {
        // Since we write content directly to disk and do not keep complex draft caches other than database states,
        // clearing editor cache can reset the open state.
        _currentFileState.value = FileContentState.Idle
        // Comment out closing/clearing event to prevent Toast popup based on user request
        // _fileEvent.value = "Editor cache cleared"
    }

    fun updateSettings(newSettings: SettingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSettings(newSettings)
        }
    }

    fun importFileFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                var fileName = "imported_file_${System.currentTimeMillis()}"
                var fileSize = 0L

                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = c.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = c.getLong(sizeIndex)
                    }
                }

                // Sanitize filename to avoid path/folder discrepancies
                val sanitizedFileName = fileName.replace("/", "_").replace("\\", "_")

                val importedDir = File(context.filesDir, "imported")
                if (!importedDir.exists()) {
                    importedDir.mkdirs()
                }

                val destFile = File(importedDir, sanitizedFileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (fileSize == 0L) {
                    fileSize = destFile.length()
                }

                val extension = sanitizedFileName.substringAfterLast('.', "").lowercase()

                val existing = repository.getRecentFileByPath(destFile.absolutePath)
                val recent = RecentFileEntity(
                    id = existing?.id ?: 0,
                    path = destFile.absolutePath,
                    name = sanitizedFileName,
                    size = fileSize,
                    extension = extension,
                    lastOpened = System.currentTimeMillis()
                )

                val id = repository.insertRecentFile(recent)
                val finalRecent = recent.copy(id = if (existing != null) existing.id else id.toInt())
                
                // Index the newly imported file path
                indexFile(destFile.absolutePath)
                
                // Automatically trigger opening the imported file!
                openFile(finalRecent)
                // Comment out imported popup/toast based on user request
                // _fileEvent.value = "Imported: $sanitizedFileName"
            } catch (e: Exception) {
                _fileEvent.value = "Import failed: ${e.message}"
            }
        }
    }

    // Direct local device search states & methods
    private val _deviceSearchResults = MutableStateFlow<List<File>>(emptyList())
    val deviceSearchResults: StateFlow<List<File>> = _deviceSearchResults.asStateFlow()

    private val _isSearchingDevice = MutableStateFlow(false)
    val isSearchingDevice: StateFlow<Boolean> = _isSearchingDevice.asStateFlow()

    fun triggerStorageIndexRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            buildStorageIndex()
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchLocalFiles(query: String) {
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _deviceSearchResults.value = emptyList()
            _isSearchingDevice.value = false
            return
        }
        
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(150L) // 150ms debounce
            _isSearchingDevice.value = true
            try {
                val pattern = query.trim()
                val resultsList = mutableListOf<File>()
                
                // If the indexed list is completely empty, let's build it immediately on Dispatchers.IO
                val isEmpty = synchronized(allIndexedFiles) { allIndexedFiles.isEmpty() }
                if (isEmpty) {
                    buildStorageIndex()
                }

                // Gather from indexed snapshot
                val currentSnapshot = synchronized(allIndexedFiles) { ArrayList(allIndexedFiles) }
                for (path in currentSnapshot) {
                    val file = File(path)
                    if (file.name.contains(pattern, ignoreCase = true)) {
                        resultsList.add(file)
                        if (resultsList.size >= 150) {
                            break
                        }
                    }
                }

                // Removed live-scan to use only existing indexes (Room & memory cache)

                _deviceSearchResults.value = resultsList.distinctBy { it.absolutePath }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSearchingDevice.value = false
            }
        }
    }

    fun importAndOpenFile(file: File) {
        _currentFileState.value = FileContentState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val extension = file.name.substringAfterLast('.', "").lowercase()
                val existing = repository.getRecentFileByPath(file.absolutePath)
                val recentFile = RecentFileEntity(
                    id = existing?.id ?: 0,
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    extension = extension,
                    lastOpened = System.currentTimeMillis(),
                    isSample = false
                )
                val id = repository.insertRecentFile(recentFile)
                
                // Index this file dynamically
                indexFile(file.absolutePath)

                val savedEntity = recentFile.copy(id = if (existing != null) existing.id else id.toInt())
                openFile(savedEntity)
            } catch (e: Exception) {
                _currentFileState.value = FileContentState.Error("Failed to open local file: ${e.message}")
            }
        }
    }
}
