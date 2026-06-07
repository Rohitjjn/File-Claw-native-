package com.example.services

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.StringBuilder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class FileManager {

    data class ZipNode(
        val name: String,
        val isDirectory: Boolean,
        val path: String,
        val size: Long = 0,
        val children: MutableList<ZipNode> = mutableListOf()
    )

    fun createSampleFilesIfNotExist(context: Context): List<File> {
        val samplesDir = File(context.filesDir, "samples")
        if (!samplesDir.exists()) {
            samplesDir.mkdirs()
        }

        val result = mutableListOf<File>()

        // 1. Markdown file: AI_Model_Census.md
        val mdFile = File(samplesDir, "AI_Model_Census.md")
        if (!mdFile.exists()) {
            mdFile.writeText("""
# Chronological AI Model Census

Welcome to the census of pioneering AI models. Here we track historical and contemporary developments in intelligence engines.

## Essential Summary
*   **Founded**: 2015
*   **Headquarters**: San Francisco, CA, USA
*   **Website**: https://openai.com
*   **Total Publicly Released Models**: ~103

## Chronological Model Timeline

| Model Name | Release Date | Type | Key Specs | Status |
| :--- | :--- | :--- | :--- | :--- |
| OpenAI Gym | 2016 | RL Environment | Toolkit for developing and comparing reinforcement learning algorithms | Active (open-source) |
| OpenAI Universe | 2016 | RL Platform | Software platform for measuring/training AI across games, websites | Retired |
| GPT-1 | 2018 | Language Model | 117M params; 12-layer transformer decoder | Research |
| GPT-2 | 2019 | Language Model | 1.5B params; Zero-shot learning cap | Open weight |
| GPT-3 | 2020 | Language Model | 175B params; Few-shot learner | Active (API) |
| DALL-E | 2021 | Text-to-Image | 12B params; Zero-shot text-to-image generator | Retired |
| ChatGPT | 2022 | Chat Assistant | Bottom conversational reinforcement learning feedback | Active |
| GPT-4 | 2023 | Multimodal | Benchmark-redefining language & vision understanding | Active |
| Gemini 1.5 Pro | 2024 | Multimodal | Dynamic context reasoning, extreme efficiency | Active |
| Gemini 3.5 Flash | 2026 | Agent Multimodal | Extreme latency optimization and speed | Active |

## Visualizing Progress
As models increase in parameter scale and structural flexibility, safety alignment processes (such as RLHF and Constitutional AI) have moved from optional post-processing to core architectural constraints.
            """.trimIndent())
        }
        result.add(mdFile)

        // 2. Kotlin code file: Theme.kt
        val codeFile = File(samplesDir, "Theme.kt")
        if (!codeFile.exists()) {
            codeFile.writeText("""
package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Claude-inspired warm minimalist palette
val ClaudePrim = Color(0xFFD97757) // Terracotta Ginger
val ClaudeSec = Color(0xFFE89D82) // Soft Peach
val ClaudeBgL = Color(0xFFFAF9F8) // Alabaster Off-white
val ClaudeSurfL = Color(0xFFFFFFFF) // Pure White
val ClaudeCharcoal = Color(0xFF201F1E) // Slate Dark Text
val ClaudeBorderL = Color(0xFFEDEBE9)

val ClaudeBgD = Color(0xFF1C1C1E) // Dark Onyx
val ClaudeSurfD = Color(0xFF2C2C2E)
val ClaudeBorderD = Color(0xFF38383A)
val ClaudeOnSecD = Color(0xFFEBEBF5)

private val LightColorScheme = lightColorScheme(
    primary = ClaudePrim,
    secondary = ClaudeSec,
    background = ClaudeBgL,
    surface = ClaudeSurfL,
    onPrimary = Color.White,
    onSecondary = ClaudeCharcoal,
    onBackground = ClaudeCharcoal,
    onSurface = ClaudeCharcoal
)

private val DarkColorScheme = darkColorScheme(
    primary = ClaudePrim,
    secondary = ClaudeSec,
    background = ClaudeBgD,
    surface = ClaudeSurfD,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun FilesClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
            """.trimIndent())
        }
        result.add(codeFile)

        // 3. CSV file: simple_sheet.csv
        val csvFile = File(samplesDir, "simple_sheet.csv")
        if (!csvFile.exists()) {
            csvFile.writeText("""
Month,Sales,Expenses,Category,Growth
January,12200,8500,A,12%
February,14300,9200,A,15%
March,15600,10400,B,8%
April,18900,11000,B,21%
May,20400,12500,C,10%
June,23100,13200,C,14%
            """.trimIndent())
        }
        result.add(csvFile)

        // 4. Text file: Welcome_Notes.txt
        val txtFile = File(samplesDir, "Welcome_Notes.txt")
        if (!txtFile.exists()) {
            txtFile.writeText("""
### WELCOME TO FILES CLAW ###

Files Claw is your ultimate offline safe-space for previewing, browsing, and refining local documents. Styled with a Claude-inspired warm terracotta minimalist design, the app contains:

1. Interactive Markdown Preview: Elegant tables, bullet points, headers, and formatted links.
2. Code Syntax highlighters and Plain Text Editors: Word wrap toggles, typography scale, tab-spacing adjustments, and real-time line numbers!
3. ZIP Archive Tree Navigator: Traverse directories inside any compressed .zip file without extraction.
4. Rich CSV Data Sheets: Preview raw CSV datasets inside styled, clean, spreadsheet-like scrolling rows.

Designed to assist files manipulation completely offline, preventing telemetry leaks and ensuring total privacy.

Thank you for choosing Files Claw.
            """.trimIndent())
        }
        result.add(txtFile)

        // 5. ZIP file: app_blueprint_package.zip
        val zipFile = File(samplesDir, "app_blueprint_package.zip")
        if (!zipFile.exists()) {
            try {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    val entries = mapOf(
                        "docs/APP_OVERVIEW.md" to "Files Claw is a local-only viewer, optimized for privacy.",
                        "docs/DESIGN_SYSTEM.md" to "Inspired by Anthropic Claude aesthetic. Primary scale uses Warm Terracotta Ginger (#D97757).",
                        "docs/COLOR_SYSTEM.md" to "Base Alabaster Background, Warm Charcoal body typography.",
                        "assets_inventory/ASSETS.md" to "Includes vector launcher icons and native layout graphics."
                    )
                    for ((name, content) in entries) {
                        val entry = ZipEntry(name)
                        zos.putNextEntry(entry)
                        zos.write(content.toByteArray())
                        zos.closeEntry()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        result.add(zipFile)

        return result
    }

    fun readFileContent(filePath: String, encoding: String = "UTF-8"): String {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.readText(charset(encoding))
            } else {
                "Error: File does not exist or is a directory: $filePath"
            }
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    fun writeFileContent(filePath: String, content: String, encoding: String = "UTF-8") {
        val file = File(filePath)
        file.writeText(content, charset(encoding))
    }

    fun parseCsv(filePath: String): List<List<String>> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        val lines = file.readLines()
        return lines.map { line ->
            val result = mutableListOf<String>()
            val currentToken = StringBuilder()
            var inQuotes = false
            for (char in line) {
                if (char == '"') {
                    inQuotes = !inQuotes
                } else if (char == ',' && !inQuotes) {
                    result.add(currentToken.toString().trim())
                    currentToken.setLength(0)
                } else {
                    currentToken.append(char)
                }
            }
            result.add(currentToken.toString().trim())
            result
        }
    }

    fun parseZipStructure(filePath: String): ZipNode {
        val file = File(filePath)
        val root = ZipNode(name = file.name, isDirectory = true, path = "", size = file.length())
        if (!file.exists()) return root

        try {
            ZipFile(file).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val parts = entry.name.split("/").filter { it.isNotEmpty() }
                    var current = root

                    var currentPath = ""
                    for (i in parts.indices) {
                        val partName = parts[i]
                        currentPath = if (currentPath.isEmpty()) partName else "$currentPath/$partName"
                        val isLast = (i == parts.lastIndex)
                        val isDir = entry.isDirectory || (!isLast)

                        var child = current.children.find { it.name == partName && it.isDirectory == isDir }
                        if (child == null) {
                            child = ZipNode(
                                name = partName,
                                isDirectory = isDir,
                                path = currentPath,
                                size = if (isLast && !entry.isDirectory) entry.size else 0
                            )
                            current.children.add(child)
                        }
                        current = child
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort folders first, then files alphabetically
        fun sortNode(node: ZipNode) {
            node.children.sortWith(compareByDescending<ZipNode> { it.isDirectory }.thenBy { it.name })
            node.children.forEach { sortNode(it) }
        }
        sortNode(root)

        return root
    }

    fun extractZipEntry(zipFilePath: String, entryPath: String, destFile: File): Boolean {
        return try {
            ZipFile(File(zipFilePath)).use { zipFile ->
                val entry = zipFile.getEntry(entryPath) ?: return false
                zipFile.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun updateZipEntry(zipFilePath: String, entryPath: String, entrySrcFile: File): Boolean {
        val zipFile = File(zipFilePath)
        if (!zipFile.exists()) return false
        val tempZip = File(zipFile.parentFile, "temp_${System.currentTimeMillis()}.zip")
        try {
            ZipFile(zipFile).use { srcZipFile ->
                ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
                    val entries = srcZipFile.entries()
                    var replaced = false
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val newEntry = ZipEntry(entry.name)
                        zos.putNextEntry(newEntry)
                        if (entry.name == entryPath) {
                            FileInputStream(entrySrcFile).use { fis ->
                                fis.copyTo(zos)
                            }
                            replaced = true
                        } else {
                            srcZipFile.getInputStream(entry).use { input ->
                                input.copyTo(zos)
                            }
                        }
                        zos.closeEntry()
                    }
                    if (!replaced) {
                        val newEntry = ZipEntry(entryPath)
                        zos.putNextEntry(newEntry)
                        FileInputStream(entrySrcFile).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            if (zipFile.delete()) {
                tempZip.renameTo(zipFile)
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempZip.exists()) tempZip.delete()
            return false
        }
    }

    sealed class DocxElement {
        data class Paragraph(
            val text: String,
            val isBold: Boolean = false,
            val isItalic: Boolean = false,
            val isHeading: Boolean = false,
            val headingLevel: Int = 0
        ) : DocxElement()
        data class Image(val localPath: String) : DocxElement()
        data class Table(val rows: List<List<String>>) : DocxElement()
        object PageBreak : DocxElement()
    }

    fun parseDocx(docxPath: String, context: Context): List<DocxElement> {
        val elements = mutableListOf<DocxElement>()
        val mediaFiles = mutableListOf<String>()
        try {
            val cacheDir = context.cacheDir
            val docxMediaDir = File(cacheDir, "docx_media_${docxPath.hashCode()}")
            if (docxMediaDir.exists()) {
                docxMediaDir.deleteRecursively()
            }
            docxMediaDir.mkdirs()
            
            ZipFile(File(docxPath)).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("word/media/") && !entry.isDirectory) {
                        try {
                            val name = entry.name.substringAfterLast("/")
                            val outFile = File(docxMediaDir, name)
                            zipFile.getInputStream(entry).use { input ->
                                outFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            mediaFiles.add(outFile.absolutePath)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            }
            mediaFiles.sort()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var imageIndex = 0
        try {
            ZipFile(File(docxPath)).use { zipFile ->
                val entry = zipFile.getEntry("word/document.xml") ?: return@use
                zipFile.getInputStream(entry).use { inputStream ->
                    val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(inputStream, "UTF-8")
                    var eventType = parser.eventType
                    
                    var currentParagraph = StringBuilder()
                    var isBoldParagraph = false
                    var isItalicParagraph = false
                    var headingLevel = 0
                    
                    var inTable = false
                    val currentTableRows = mutableListOf<MutableList<String>>()
                    var currentTableRow = mutableListOf<String>()
                    var currentTableCell = StringBuilder()
                    
                    var inText = false
                    var inBold = false
                    var inItalic = false
                    
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        val tagName = parser.name
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                            when {
                                tagName == "tbl" || tagName == "w:tbl" || (tagName != null && tagName.endsWith(":tbl")) -> {
                                    inTable = true
                                    currentTableRows.clear()
                                }
                                tagName == "tr" || tagName == "w:tr" || (tagName != null && tagName.endsWith(":tr")) -> {
                                    currentTableRow = mutableListOf()
                                }
                                tagName == "tc" || tagName == "w:tc" || (tagName != null && tagName.endsWith(":tc")) -> {
                                    currentTableCell = StringBuilder()
                                }
                                tagName == "p" || tagName == "w:p" || (tagName != null && tagName.endsWith(":p")) -> {
                                    currentParagraph = StringBuilder()
                                    isBoldParagraph = false
                                    isItalicParagraph = false
                                    headingLevel = 0
                                }
                                tagName == "pStyle" || tagName == "w:pStyle" || (tagName != null && tagName.endsWith(":pStyle")) -> {
                                    val styleVal = parser.getAttributeValue(null, "w:val") ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                    if (styleVal != null) {
                                        if (styleVal.startsWith("Heading", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true)) {
                                            headingLevel = styleVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                        }
                                    }
                                }
                                tagName == "b" || tagName == "w:b" || (tagName != null && tagName.endsWith(":b")) -> {
                                    inBold = true
                                }
                                tagName == "i" || tagName == "w:i" || (tagName != null && tagName.endsWith(":i")) -> {
                                    inItalic = true
                                }
                                tagName == "t" || tagName == "w:t" || (tagName != null && tagName.endsWith(":t")) -> {
                                    inText = true
                                }
                                tagName == "br" || tagName == "w:br" || (tagName != null && tagName.endsWith(":br")) -> {
                                    val brType = parser.getAttributeValue(null, "w:type") ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "type")
                                    if (brType == "page") {
                                        elements.add(DocxElement.PageBreak)
                                    }
                                }
                                tagName == "lastRenderedPageBreak" || tagName == "w:lastRenderedPageBreak" || (tagName != null && tagName.endsWith(":lastRenderedPageBreak")) -> {
                                    elements.add(DocxElement.PageBreak)
                                }
                                tagName == "drawing" || tagName == "w:drawing" || (tagName != null && tagName.endsWith(":drawing")) -> {
                                    if (imageIndex < mediaFiles.size) {
                                        elements.add(DocxElement.Image(mediaFiles[imageIndex]))
                                        imageIndex++
                                    }
                                }
                            }
                        } else if (eventType == org.xmlpull.v1.XmlPullParser.TEXT) {
                            if (inText && parser.text != null) {
                                val textVal = parser.text
                                if (inTable) {
                                    currentTableCell.append(textVal)
                                } else {
                                    currentParagraph.append(textVal)
                                    if (inBold) isBoldParagraph = true
                                    if (inItalic) isItalicParagraph = true
                                }
                            }
                        } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                            when {
                                tagName == "tbl" || tagName == "w:tbl" || (tagName != null && tagName.endsWith(":tbl")) -> {
                                    inTable = false
                                    elements.add(DocxElement.Table(currentTableRows.map { it.toList() }))
                                }
                                tagName == "tr" || tagName == "w:tr" || (tagName != null && tagName.endsWith(":tr")) -> {
                                    if (inTable) {
                                        currentTableRows.add(currentTableRow)
                                    }
                                }
                                tagName == "tc" || tagName == "w:tc" || (tagName != null && tagName.endsWith(":tc")) -> {
                                    if (inTable) {
                                        currentTableRow.add(currentTableCell.toString().trim())
                                    }
                                }
                                tagName == "p" || tagName == "w:p" || (tagName != null && tagName.endsWith(":p")) -> {
                                    if (!inTable) {
                                        val text = currentParagraph.toString().trim()
                                        if (text.isNotEmpty() || (eventType == org.xmlpull.v1.XmlPullParser.END_TAG && currentParagraph.isNotEmpty())) {
                                            elements.add(
                                                DocxElement.Paragraph(
                                                    text = text,
                                                    isBold = isBoldParagraph,
                                                    isItalic = isItalicParagraph,
                                                    isHeading = (headingLevel > 0),
                                                    headingLevel = headingLevel
                                                )
                                            )
                                        }
                                    }
                                }
                                tagName == "b" || tagName == "w:b" || (tagName != null && tagName.endsWith(":b")) -> {
                                    inBold = false
                                }
                                tagName == "i" || tagName == "w:i" || (tagName != null && tagName.endsWith(":i")) -> {
                                    inItalic = false
                                }
                                tagName == "t" || tagName == "w:t" || (tagName != null && tagName.endsWith(":t")) -> {
                                    inText = false
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (elements.isEmpty()) {
            val fallbackText = readDocxText(docxPath)
            elements.add(DocxElement.Paragraph(text = fallbackText))
        }
        return elements
    }

    fun readDocxText(docxPath: String): String {
        val textBuilder = StringBuilder()
        try {
            java.util.zip.ZipFile(File(docxPath)).use { zipFile ->
                val entry = zipFile.getEntry("word/document.xml") ?: return "No readable body text found in DOCX structure."
                zipFile.getInputStream(entry).use { inputStream ->
                    val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(inputStream, "UTF-8")
                    var eventType = parser.eventType
                    var inTextTag = false
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        val tagName = parser.name
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                            if (tagName == "t" || tagName == "w:t" || (tagName != null && tagName.endsWith(":t"))) {
                                inTextTag = true
                            } else if (tagName == "p" || tagName == "w:p" || (tagName != null && tagName.endsWith(":p"))) {
                                if (textBuilder.isNotEmpty() && !textBuilder.endsWith("\n")) {
                                    textBuilder.append("\n")
                                }
                            }
                        } else if (eventType == org.xmlpull.v1.XmlPullParser.TEXT) {
                            if (inTextTag && parser.text != null) {
                                textBuilder.append(parser.text)
                            }
                        } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                            if (tagName == "t" || tagName == "w:t" || (tagName != null && tagName.endsWith(":t"))) {
                                inTextTag = false
                            }
                        }
                        eventType = parser.next()
                    }
                }
            }
        } catch (e: Exception) {
            return "Failed to open Word template document: ${e.message}"
        }
        return textBuilder.toString()
    }

    fun writeDocxText(docxPath: String, newContent: String): Boolean {
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        xmlBuilder.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
        xmlBuilder.append("<w:body>")
        for (line in newContent.split("\n")) {
            val sanitized = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            xmlBuilder.append("<w:p><w:r><w:t>").append(sanitized).append("</w:t></w:r></w:p>")
        }
        xmlBuilder.append("</w:body></w:document>")
        val xmlData = xmlBuilder.toString().toByteArray(Charsets.UTF_8)

        val zipFile = File(docxPath)
        val tempFile = File(zipFile.parentFile, "edit_temp_${System.currentTimeMillis()}.docx")
        try {
            java.util.zip.ZipInputStream(FileInputStream(zipFile)).use { zis ->
                java.util.zip.ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                    var entry = zis.nextEntry
                    var replaced = false
                    while (entry != null) {
                        if (entry.name != "word/document.xml") {
                            zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                            zis.copyTo(zos)
                        } else {
                            zos.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
                            zos.write(xmlData)
                            replaced = true
                        }
                        zos.closeEntry()
                        entry = zis.nextEntry
                    }
                    if (!replaced) {
                        zos.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
                        zos.write(xmlData)
                        zos.closeEntry()
                    }
                }
            }
            if (zipFile.delete()) {
                tempFile.renameTo(zipFile)
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            return false
        }
    }

    fun readPptxText(pptxPath: String): String {
        val textBuilder = StringBuilder()
        try {
            java.util.zip.ZipFile(File(pptxPath)).use { zipFile ->
                val entries = zipFile.entries()
                val sortedEntries = mutableListOf<java.util.zip.ZipEntry>()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        sortedEntries.add(entry)
                    }
                }
                sortedEntries.sortBy { it.name }
                for (entry in sortedEntries) {
                    val slideNum = entry.name.substringAfter("slide").substringBefore(".xml")
                    textBuilder.append("--- Slide $slideNum ---\n")
                    zipFile.getInputStream(entry).use { inputStream ->
                        val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
                        parser.setInput(inputStream, "UTF-8")
                        var eventType = parser.eventType
                        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "t") {
                                textBuilder.append(parser.nextText()).append(" ")
                            }
                            eventType = parser.next()
                        }
                    }
                    textBuilder.append("\n\n")
                }
            }
        } catch (e: Exception) {
            return "Failed to open PowerPoint slides: ${e.message}"
        }
        return textBuilder.toString().trim()
    }

    fun writePptxText(pptxPath: String, newContent: String): Boolean {
        // Writes edited presentation slide texts by taking lines and bundling them as a clean slide content template
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        xmlBuilder.append("<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">")
        xmlBuilder.append("<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>")
        
        var idCounter = 10
        for (line in newContent.split("\n")) {
            if (line.isBlank() || line.startsWith("--- Slide")) continue
            val sanitized = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            idCounter++
            xmlBuilder.append("<p:sp><p:nvSpPr><p:cNvPr id=\"$idCounter\" name=\"Text Box\"/><p:cNvSpPr><p:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr/></p:nvSpPr><p:spPr/>")
            xmlBuilder.append("<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"en-US\" sz=\"1800\"/><a:t>$sanitized</a:t></a:r></a:p></p:txBody></p:sp>")
        }
        xmlBuilder.append("</p:cSld></p:spTree></p:cSld></p:sld>")
        val xmlData = xmlBuilder.toString().toByteArray(Charsets.UTF_8)

        val zipFile = File(pptxPath)
        val tempFile = File(zipFile.parentFile, "edit_temp_${System.currentTimeMillis()}.pptx")
        try {
            java.util.zip.ZipInputStream(FileInputStream(zipFile)).use { zis ->
                java.util.zip.ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                    var entry = zis.nextEntry
                    var replaced = false
                    while (entry != null) {
                        if (entry.name == "ppt/slides/slide1.xml") {
                            zos.putNextEntry(java.util.zip.ZipEntry("ppt/slides/slide1.xml"))
                            zos.write(xmlData)
                            replaced = true
                        } else {
                            zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                            zis.copyTo(zos)
                        }
                        zos.closeEntry()
                        entry = zis.nextEntry
                    }
                    if (!replaced) {
                        zos.putNextEntry(java.util.zip.ZipEntry("ppt/slides/slide1.xml"))
                        zos.write(xmlData)
                        zos.closeEntry()
                    }
                }
            }
            if (zipFile.delete()) {
                tempFile.renameTo(zipFile)
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            if (tempFile.exists()) tempFile.delete()
            return false
        }
    }

    fun searchLocalDeviceFiles(query: String): List<File> {
        if (query.trim().length < 2) return emptyList()
        val results = mutableListOf<File>()
        val scanDirs = listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/sdcard")
        )
        
        for (dir in scanDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            try {
                recurseScan(dir, query, results, maxResultsNum = 20)
                if (results.size >= 20) break
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return results
    }

    private fun recurseScan(dir: File, query: String, results: MutableList<File>, maxResultsNum: Int) {
        if (results.size >= maxResultsNum) return
        val filesList = dir.listFiles() ?: return
        for (f in filesList) {
            if (results.size >= maxResultsNum) return
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    recurseScan(f, query, results, maxResultsNum)
                }
            } else if (f.isFile) {
                if (f.name.contains(query, ignoreCase = true)) {
                    results.add(f)
                }
            }
        }
    }
}
