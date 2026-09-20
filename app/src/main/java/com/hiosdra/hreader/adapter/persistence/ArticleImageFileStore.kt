package com.hiosdra.hreader.adapter.persistence

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

internal class ArticleImageFileStore(
    context: Context,
    private val fileExists: (String) -> Boolean = { path -> File(path).exists() }
) {
    val directory = File(context.filesDir, "article_images").also { imagesDirectory ->
        imagesDirectory.mkdirs()
        imagesDirectory.listFiles { file ->
            file.name.startsWith(".") && file.name.endsWith(".tmp")
        }?.forEach(File::delete)
    }

    fun exists(path: String): Boolean = fileExists(path)

    fun target(imageId: String, extension: String): File = File(directory, "$imageId$extension")

    fun staging(imageId: String): File = File(directory, ".$imageId-${UUID.randomUUID()}.tmp")

    fun move(staging: File, target: File) {
        try {
            Files.move(
                staging.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                staging.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    fun clearAll() {
        directory.listFiles()?.forEach(File::deleteRecursively)
    }

    fun files(): List<File> = directory.listFiles()?.toList().orEmpty()

    fun delete(file: File) {
        file.delete()
    }

    companion object {
        fun imageId(entryId: Long, imageUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest("$entryId-$imageUrl".toByteArray(UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
