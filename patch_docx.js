const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/services/FileManager.kt', 'utf8');

const oldParseDocxStart = content.indexOf('fun parseDocx(docxPath: String, context: Context): List<DocxElement> {');
const oldParseDocxEnd = content.indexOf('fun readDocxText(', oldParseDocxStart);

if (oldParseDocxStart === -1 || oldParseDocxEnd === -1) {
    console.error("Could not find boundaries");
    process.exit(1);
}

const newParseDocx = `fun parseDocx(docxPath: String, context: Context): List<DocxElement> {
        val elements = mutableListOf<DocxElement>()
        val mediaFiles = mutableListOf<String>()
        try {
            val cacheDir = context.cacheDir
            val docxMediaDir = File(cacheDir, "docx_media_\${docxPath.hashCode()}")
            if (docxMediaDir.exists()) docxMediaDir.deleteRecursively()
            docxMediaDir.mkdirs()
            
            java.util.zip.ZipFile(File(docxPath)).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("word/media/") && !entry.isDirectory) {
                        try {
                            val name = entry.name.substringAfterLast("/")
                            val outFile = File(docxMediaDir, name)
                            zipFile.getInputStream(entry).use { input ->
                                outFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            mediaFiles.add(outFile.absolutePath)
                        } catch (e: Exception) {}
                    }
                }
            }
            mediaFiles.sort()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var imageIndex = 0
        try {
            java.util.zip.ZipFile(File(docxPath)).use { zipFile ->
                val entry = zipFile.getEntry("word/document.xml") ?: return@use
                zipFile.getInputStream(entry).use { inputStream ->
                    val xmlContent = inputStream.bufferedReader().use { it.readText() }
                    
                    val paragraphRegex = Regex("<w:p\\\\b[^>]*>(.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
                    val textRegex = Regex("<w:t\\\\b[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                    val drawingRegex = Regex("<w:drawing\\\\b[^>]*>")
                    
                    val paragraphs = paragraphRegex.findAll(xmlContent)
                    for (pMatch in paragraphs) {
                        val pContent = pMatch.groups[1]?.value ?: ""
                        
                        if (drawingRegex.containsMatchIn(pContent)) {
                            if (imageIndex < mediaFiles.size) {
                                elements.add(DocxElement.Image(mediaFiles[imageIndex]))
                                imageIndex++
                            }
                        }
                        
                        val texts = textRegex.findAll(pContent)
                        val fullText = StringBuilder()
                        for (tMatch in texts) {
                            var t = tMatch.groups[1]?.value ?: ""
                            t = t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", "\\\"").replace("&apos;", "'")
                            fullText.append(t)
                        }
                        
                        val finalText = fullText.toString().trim()
                        if (finalText.isNotEmpty()) {
                            elements.add(DocxElement.Paragraph(finalText, isBold = false, isItalic = false, isHeading = false, headingLevel = 0))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return elements
    }

    `;

content = content.substring(0, oldParseDocxStart) + newParseDocx + content.substring(oldParseDocxEnd);
fs.writeFileSync('/app/applet/app/src/main/java/com/example/services/FileManager.kt', content);
