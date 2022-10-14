package ___PACKAGE___

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.databinding.BindingAdapter
import com.qmobile.qmobileui.ui.setOnSingleClickListener
import com.qmobile.qmobileui.utils.PermissionChecker
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private const val CLICK_DEBOUNCE_TIME = 2000L

@BindingAdapter("downloadActivityAction")
fun downloadActivityAction(view: TextView, urlString: String?) {
    if (urlString.isNullOrEmpty()) return
    view.text = urlString

    if (!Patterns.WEB_URL.toRegex().matches(urlString))
        return

    var inProgress = false
    view.setOnSingleClickListener(CLICK_DEBOUNCE_TIME) {
        askPermission(view.context) {
            if (!inProgress) {
                inProgress = true
                downloadImage(view.context, urlString) {
                    inProgress = false
                }
            }
        }
    }
}

private fun askPermission(context: Context, canGoOn: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        (context as? PermissionChecker)?.askPermission(
            context = context,
            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            rationale = "Permission required to save files to your download folder"
        ) { isGranted ->
            if (isGranted) {
                canGoOn()
            }
        }
    } else {
        canGoOn()
    }
}

fun downloadImage(context: Context, url: String, onFinished: () -> Unit) {
    val directory = File(Environment.DIRECTORY_DOWNLOADS)

    if (!directory.exists()) {
        directory.mkdirs()
    }

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val fileName = url.substring(url.lastIndexOf("/") + 1)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setAllowedOverRoaming(false)
            .setTitle(fileName)
            .setDescription("")
            .setDestinationInExternalPublicDir(
                directory.toString(),
                fileName
            )
    }

    val downloadId = downloadManager.enqueue(request)
    val query = DownloadManager.Query().setFilterById(downloadId)

    val executor = Executors.newSingleThreadExecutor()
    val handler = Handler(Looper.getMainLooper())

    var downloadFilePath: String

    executor.execute {
        var downloading = true
        var lastMsg = ""
        var status: Int
        while (downloading) {
            val cursor: Cursor = downloadManager.query(query)
            cursor.moveToFirst()
            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            status = cursor.getInt(columnIndex)
            val msg = statusMessage(status)
            if (msg != lastMsg && msg.isNotEmpty()) {
                handler.post {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                lastMsg = msg
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloading = false
                    val columnIndexLocalUri = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    downloadFilePath = cursor.getString(columnIndexLocalUri).replace("file://", "")
                    val downloadedFile = File(downloadFilePath)
                    if (downloadedFile.exists()) {
                        startFileShareIntent(context, downloadedFile)
                    }
                    onFinished()
                }
                DownloadManager.STATUS_FAILED -> {
                    downloading = false
                    downloadManager.remove(downloadId)
                    onFinished()
                }
            }
            cursor.close()
        }
    }
}

private fun statusMessage(status: Int): String = when (status) {
    DownloadManager.STATUS_FAILED -> "Download failed"
    DownloadManager.STATUS_RUNNING -> "Downloading..."
    DownloadManager.STATUS_SUCCESSFUL -> "Download successful"
    else -> ""
}

private fun startFileShareIntent(context: Context, file: File) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = file.getMimeType()
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val fileURI = FileProvider.getUriForFile(context, context.packageName + ".provider", File(file.path))
        putExtra(Intent.EXTRA_STREAM, fileURI)
    }
    context.startActivity(shareIntent)
}

fun File.getMimeType(): String = MimeTypeMap.getFileExtensionFromUrl(toUri().toString())
    ?.run { MimeTypeMap.getSingleton().getMimeTypeFromExtension(lowercase(Locale.getDefault())) }
    ?: "*/*"
