package com.example.data.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.domain.model.FileCategory
import com.example.domain.model.SelectableFileItem
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

class DeviceFileScanner(private val context: Context) {

    fun getAppShareDirectory(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(baseDir, "WiFiShare").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getStagingDirectory(): File {
        return File(context.cacheDir, "wifishare_part").apply {
            if (!exists()) mkdirs()
        }
    }

    fun scanAppShareDirectory(): List<SelectableFileItem> {
        val root = getAppShareDirectory()
        val children = root.listFiles()?.sortedByDescending { it.lastModified() } ?: return emptyList()
        return children.map { file ->
            if (file.isDirectory) {
                val (count, totalSize) = measureLocalFolder(file)
                SelectableFileItem(
                    id = file.absolutePath,
                    name = file.name,
                    uriString = Uri.fromFile(file).toString(),
                    sizeBytes = totalSize,
                    category = FileCategory.FOLDER,
                    isFolder = true,
                    fileCount = count,
                    mimeType = "vnd.android.document/directory",
                    extensionLabel = "FOLDER",
                )
            } else {
                val ext = file.extension.uppercase().ifEmpty { "FILE" }
                val mime = guessMimeType(file.name)
                SelectableFileItem(
                    id = file.absolutePath,
                    name = file.name,
                    uriString = Uri.fromFile(file).toString(),
                    sizeBytes = file.length(),
                    category = categorizeFile(file.name, mime, isFolder = false),
                    isFolder = false,
                    fileCount = 1,
                    mimeType = mime,
                    extensionLabel = ext,
                )
            }
        }
    }

    fun inspectPickedUris(uris: List<Uri>): List<SelectableFileItem> {
        return uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            var displayName = "ملف_${System.currentTimeMillis()}"
            var sizeBytes = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
            val mime = context.contentResolver.getType(uri) ?: guessMimeType(displayName)
            val ext = displayName.substringAfterLast('.', "").uppercase().ifEmpty {
                mime.substringAfter('/', "FILE").uppercase()
            }
            SelectableFileItem(
                id = uri.toString(),
                name = displayName,
                uriString = uri.toString(),
                sizeBytes = sizeBytes,
                category = categorizeFile(displayName, mime, isFolder = false),
                isFolder = false,
                fileCount = 1,
                mimeType = mime,
                extensionLabel = ext,
                isSelected = true,
            )
        }
    }

    fun inspectPickedTreeUri(treeUri: Uri): SelectableFileItem? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        var folderName = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "مجلد محدد" }
        context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                folderName = cursor.getString(0) ?: folderName
            }
        }
        val (count, totalBytes) = measureDocumentTree(treeUri, docId)
        return SelectableFileItem(
            id = treeUri.toString(),
            name = folderName,
            uriString = treeUri.toString(),
            sizeBytes = totalBytes,
            category = FileCategory.FOLDER,
            isFolder = true,
            fileCount = count.coerceAtLeast(1),
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            extensionLabel = "FOLDER",
            isSelected = true,
        )
    }

    fun openStreamForItem(uriString: String): InputStream? {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "file" -> FileInputStream(File(uri.path ?: return null))
            "content" -> context.contentResolver.openInputStream(uri)
            else -> null
        }
    }

    fun saveCompletedFile(
        fileName: String,
        mimeType: String,
        stagedFile: File,
    ): String {
        val safeName = fileName.ifBlank { "received_${UUID.randomUUID().toString().take(6)}" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/WiFiShare")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (insertedUri != null) {
                val copied = runCatching {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        stagedFile.inputStream().use { input -> input.copyTo(out, bufferSize = 64 * 1024) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(insertedUri, values, null, null)
                }.isSuccess
                if (copied) {
                    stagedFile.delete()
                    return insertedUri.toString()
                }
            }
        }
        val target = File(getAppShareDirectory(), safeName)
        stagedFile.copyTo(target, overwrite = true)
        stagedFile.delete()
        return Uri.fromFile(target).toString()
    }

    private fun measureLocalFolder(folder: File): Pair<Int, Long> {
        var count = 0
        var size = 0L
        folder.walkTopDown().forEach { entry ->
            if (entry.isFile) {
                count++
                size += entry.length()
            }
        }
        return count to size
    }

    private fun measureDocumentTree(treeUri: Uri, parentDocId: String, depth: Int = 0): Pair<Int, Long> {
        if (depth > 4) return 0 to 0L
        var count = 0
        var size = 0L
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(0) ?: continue
                    val mime = cursor.getString(1).orEmpty()
                    val itemSize = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val (subCount, subSize) = measureDocumentTree(treeUri, childId, depth + 1)
                        count += subCount
                        size += subSize
                    } else {
                        count++
                        size += itemSize
                    }
                }
            }
        }
        return count to size
    }

    private fun categorizeFile(name: String, mime: String, isFolder: Boolean): FileCategory {
        if (isFolder) return FileCategory.FOLDER
        val lowerName = name.lowercase()
        return when {
            lowerName.endsWith(".apk") || mime == "application/vnd.android.package-archive" -> FileCategory.APP
            mime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") -> FileCategory.IMAGE
            mime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                lowerName.endsWith(".mov") -> FileCategory.VIDEO
            else -> FileCategory.DOCUMENT
        }
    }

    private fun guessMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "mp4", "mkv", "mov" -> "video/$ext"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
