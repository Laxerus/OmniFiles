package dev.laxerus.omnifiles.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object LocalFileIntents {
    fun requireDirectFile(file: File): File {
        val absolute = file.absoluteFile
        val canonical = file.canonicalFile
        require(absolute.path == canonical.path) { "Sembolik bağlantı veya dolaylı dosya yolu kullanılamaz" }
        require(canonical.isFile) { "Dosya artık mevcut değil" }
        return canonical
    }

    fun mimeFor(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    fun contentUri(context: Context, file: File): Uri {
        val safe = requireDirectFile(file)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", safe)
    }

    fun viewIntent(context: Context, file: File): Intent {
        val safe = requireDirectFile(file)
        val uri = contentUri(context, safe)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(safe))
            clipData = ClipData.newUri(context.contentResolver, safe.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareIntent(context: Context, file: File): Intent {
        val safe = requireDirectFile(file)
        val uri = contentUri(context, safe)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(safe)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, safe.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun checksumIntent(context: Context, file: File): Intent {
        val safe = requireDirectFile(file)
        val uri = contentUri(context, safe)
        return Intent(context, ChecksumActivity::class.java).apply {
            data = uri
            clipData = ClipData.newUri(context.contentResolver, safe.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
