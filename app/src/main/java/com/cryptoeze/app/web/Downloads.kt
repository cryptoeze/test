package com.cryptoeze.app.web

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import java.io.File

/** Saves files from the website into the phone's Downloads folder. */
object Downloads {

    /** Queues an http(s) download with the system download manager (shows progress in the notification bar). */
    fun enqueue(context: Context, url: String, userAgent: String, contentDisposition: String?, mimeType: String?): String {
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            addRequestHeader("User-Agent", userAgent)
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        return name
    }

    /** Writes a `data:` URL (from a blob or inline file) into Downloads. Returns the saved file name. */
    fun saveDataUrl(context: Context, dataUrl: String, suggestedName: String, suggestedMime: String): String {
        val comma = dataUrl.indexOf(',')
        require(dataUrl.startsWith("data:") && comma > 0) { "Not a data URL" }
        val header = dataUrl.substring(5, comma)
        val mime = header.substringBefore(';').ifEmpty { suggestedMime }.ifEmpty { "application/octet-stream" }
        val bytes = if (header.contains(";base64")) {
            Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        } else {
            Uri.decode(dataUrl.substring(comma + 1)).toByteArray()
        }
        val name = fileName(suggestedName, mime)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create download")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
        }
        return name
    }

    private fun fileName(suggested: String, mime: String): String {
        val clean = suggested.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (clean.contains('.')) return clean
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val base = clean.ifEmpty { "CryptoEze_${System.currentTimeMillis()}" }
        return "$base.$ext"
    }
}
