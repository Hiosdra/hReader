package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import java.io.File
import java.util.UUID

internal class ArticlePageFileStore(context: Context) {
    val directory = File(context.filesDir, "article_pages").apply { mkdirs() }

    fun pageDirectory(entryId: Long, path: String): File? {
        val root = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        val expected = runCatching { File(root, entryId.toString()).canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it == expected }
    }

    fun stagingDirectory(entryId: Long): File =
        File(directory, ".staging-$entryId-${UUID.randomUUID()}")

    fun finalDirectory(entryId: Long): File = File(directory, entryId.toString())

    fun replaceDirectory(entryId: Long, stagingDirectory: File) {
        val finalDirectory = finalDirectory(entryId)
        val backupDirectory = File(directory, ".backup-$entryId-${UUID.randomUUID()}")
        var movedExistingDirectory = false
        try {
            if (finalDirectory.exists()) {
                check(finalDirectory.renameTo(backupDirectory)) {
                    "Could not preserve existing offline page $entryId"
                }
                movedExistingDirectory = true
            }
            check(stagingDirectory.renameTo(finalDirectory)) {
                "Could not commit offline page $entryId"
            }
            if (movedExistingDirectory) backupDirectory.deleteRecursively()
        } catch (failure: Throwable) {
            if (movedExistingDirectory) {
                finalDirectory.deleteRecursively()
                if (backupDirectory.exists()) {
                    check(backupDirectory.renameTo(finalDirectory)) {
                        "Could not restore existing offline page $entryId"
                    }
                }
            }
            throw failure
        }
    }

    fun files(): List<File> = directory.listFiles()?.toList().orEmpty()

    fun isTemporary(file: File): Boolean =
        file.name.startsWith(".staging-") || file.name.startsWith(".backup-")

    fun hasTemporaryPageDirectory(entryId: Long): Boolean = files().any { file ->
        file.name.startsWith(".staging-$entryId-") || file.name.startsWith(".backup-$entryId-")
    }

    fun clearAll() {
        files().forEach(File::deleteRecursively)
    }
}
