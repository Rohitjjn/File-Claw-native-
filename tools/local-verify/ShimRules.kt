package org.junit.rules

import java.io.File
import java.nio.file.Files

/**
 * Minimal TemporaryFolder stand-in for the Gradle-free local verification path.
 * Only the API surface used by this repo's tests is implemented:
 *  - newFolder(String): File
 *  - newFile(String): File
 * The Runner creates/cleans the folder around each test method (mirrors @Rule).
 */
class TemporaryFolder {
    private var root: File? = null

    val rootFile: File
        get() = root ?: error("TemporaryFolder has not been created yet")

    fun newFolder(name: String): File = File(rootFile, name).also {
        if (!it.isDirectory && !it.mkdirs() && !it.isDirectory) throw IllegalStateException("cannot create $it")
    }

    fun newFile(name: String): File = File(rootFile, name).also { it.createNewFile() }

    fun newFile(): File = File.createTempFile("junit", ".tmp", rootFile)

    internal fun create() {
        root = Files.createTempDirectory("junit-tempfolder").toFile()
    }

    internal fun cleanup() {
        root?.deleteRecursively()
        root = null
    }
}
