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
import java.io.File
import java.io.FileOutputStream

import java.io.FileInputStream
import java.io.InputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fileManager = FileManager()
    private val repository: AppRepository

    // Indexing for super-fast search
    private val allIndexedFiles = java.util.Collections.synchronizedList(mutableListOf<String>())

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.recentFileDao(), database.settingDao())
        
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

            // 2. Launch background search scanning script on storage to build dynamic fresh cache
            launch(Dispatchers.IO) {
                buildStorageIndex()
            }
        }
    }

    private fun buildStorageIndex() {
        val rootDir = File("/storage/emulated/0")
        val appFilesDir = getApplication<Application>().filesDir
        val externalFilesDir = getApplication<Application>().getExternalFilesDir(null)
        val freshList = mutableListOf<String>()

        fun walk(dir: File) {
            try {
                val files = dir.listFiles() ?: return
                for (f in files) {
                    val name = f.name
                    if (name.startsWith(".")) continue
                    if (f.isDirectory) {
                        if (name.equals("Android", ignoreCase = true)) continue
                        walk(f)
                    } else {
                        freshList.add(f.absolutePath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (rootDir.exists() && rootDir.isDirectory) {
            walk(rootDir)
        }
        
        // Explicitly scan public directories in case root index returns empty due to scope restrictions
        val defaultDirs = listOf("Download", "Documents", "DCIM", "Pictures", "Music")
        for (dirName in defaultDirs) {
            val sub = File(rootDir, dirName)
            if (sub.exists() && sub.isDirectory) {
                walk(sub)
            }
        }

        if (appFilesDir.exists() && appFilesDir.isDirectory) {
            walk(appFilesDir)
        }
        if (externalFilesDir != null && externalFilesDir.exists() && externalFilesDir.isDirectory) {
            walk(externalFilesDir)
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
        data class ZipSuccess(val root: FileManager.ZipNode, val file: RecentFileEntity) : FileContentState()
        data class ImageSuccess(val file: RecentFileEntity) : FileContentState()
        data class PdfSuccess(val file: RecentFileEntity) : FileContentState()
        data class DocxSuccess(val elements: List<com.example.services.FileManager.DocxElement>, val file: RecentFileEntity) : FileContentState()
        data class MediaSuccess(val file: RecentFileEntity, val isAudio: Boolean) : FileContentState()
        data class BinarySuccess(val hexRows: List<String>, val asciiRows: List<String>, val file: RecentFileEntity) : FileContentState()
        data class Error(val message: String) : FileContentState()
    }

    private val _currentFileState = MutableStateFlow<FileContentState>(FileContentState.Idle)
    val currentFileState: StateFlow<FileContentState> = _currentFileState.asStateFlow()

    private val _loadingFilePath = MutableStateFlow<String?>(null)
    val loadingFilePath: StateFlow<String?> = _loadingFilePath.asStateFlow()

    // Navigation trigger event (prevents race conditions)
    private val _navigateToPreview = MutableStateFlow(false)
    val navigateToPreview = _navigateToPreview.asStateFlow()

    fun resetNavigateToPreview() {
        _navigateToPreview.value = false
    }

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
            val cacheDir = getApplication<Application>().cacheDir
            val tempDir = File(cacheDir, "temp_preview")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            tempDir.listFiles()?.forEach { it.delete() }
            
            val tempFile = File(tempDir, file.name)
            file.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            file
        }
    }

    fun openFile(fileEntity: RecentFileEntity) {
        _currentFileState.value = FileContentState.Loading
        _loadingFilePath.value = fileEntity.path
        _navigateToPreview.value = true // Transition immediately to FilePreviewScreen for seamless modern UX
        
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
 
                val file = File(fileEntity.path)
                if (!file.exists()) {
                    _currentFileState.value = FileContentState.Error("File not found on disk. It might have been deleted.")
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
                        val elements = fileManager.parseDocx(activePath, getApplication())
                        _currentFileState.value = FileContentState.DocxSuccess(elements, finalEntity)
                    }
                    ext == "pptx" || ext == "ppt" -> {
                        val text = fileManager.readPptxText(activePath)
                        _currentFileState.value = FileContentState.TextSuccess(text, finalEntity)
                    }
                    ext == "pdf" -> {
                        _currentFileState.value = FileContentState.PdfSuccess(tempEntity)
                    }
                    ext == "csv" || ext == "tsv" -> {
                        val csvData = fileManager.parseCsv(activePath)
                        _currentFileState.value = FileContentState.CsvSuccess(csvData, finalEntity)
                    }
                    ext == "zip" -> {
                        val zipStructure = fileManager.parseZipStructure(activePath)
                        _currentFileState.value = FileContentState.ZipSuccess(zipStructure, finalEntity)
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
                
                // Comment out opening popup/toast based on user requirement
                // if (settingsState.value.notificationFileOpen) {
                //     _fileEvent.value = "Opened: ${fileEntity.name}"
                // }
            } catch (e: Exception) {
                _currentFileState.value = FileContentState.Error("Failed to open file: ${e.message}")
            } finally {
                _loadingFilePath.value = null
            }
        }
    }

    fun openZipEntry(parentZipEntity: RecentFileEntity, node: FileManager.ZipNode) {
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
                
                val extracted = fileManager.extractZipEntry(parentZipEntity.path, node.path, localFile)
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
                    _navigateToPreview.value = true
                }
            } catch (e: Exception) {
                _currentFileState.value = FileContentState.Error("Failed to open ZIP entry: ${e.message}")
                _navigateToPreview.value = true
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
                    _navigateToPreview.value = false
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
                        _navigateToPreview.value = false
                    } else {
                        _currentFileState.value = FileContentState.Error("Parent ZIP file not found on disk.")
                        _navigateToPreview.value = true
                    }
                }
            } catch (e: Exception) {
                _currentFileState.value = FileContentState.Error("Failed to reopen parent ZIP: ${e.message}")
                _navigateToPreview.value = true
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

    fun searchLocalFiles(query: String) {
        if (query.trim().isEmpty()) {
            _deviceSearchResults.value = emptyList()
            return
        }
        _isSearchingDevice.value = true
        viewModelScope.launch(Dispatchers.Default) {
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

                // If results are still very small, do a live check of the common public subdirectories to catch any newly created files
                if (resultsList.size < 50) {
                    val rootDir = File("/storage/emulated/0")
                    val defaultDirs = listOf("Download", "Documents", "DCIM", "Pictures", "Music")
                    val dirsToScan = mutableListOf<File>()
                    if (rootDir.exists()) {
                        dirsToScan.add(rootDir)
                        for (dirName in defaultDirs) {
                            val sub = File(rootDir, dirName)
                            if (sub.exists() && sub.isDirectory) {
                                dirsToScan.add(sub)
                            }
                        }
                    }
                    val appFilesDir = getApplication<Application>().filesDir
                    dirsToScan.add(appFilesDir)
                    val externalFilesDir = getApplication<Application>().getExternalFilesDir(null)
                    if (externalFilesDir != null) {
                        dirsToScan.add(externalFilesDir)
                    }

                    for (dir in dirsToScan) {
                        val files = dir.listFiles()
                        if (files != null) {
                            for (f in files) {
                                if (f.isFile && f.name.contains(pattern, ignoreCase = true)) {
                                    val absPath = f.absolutePath
                                    if (resultsList.none { it.absolutePath == absPath }) {
                                        resultsList.add(f)
                                        // Also dynamically index it for future searches
                                        indexFile(absPath)
                                    }
                                }
                            }
                        }
                    }
                }

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
