package ___PACKAGE___

import android.content.Context
import android.content.Intent
import android.os.Environment.DIRECTORY_DOWNLOADS
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.databinding.BindingAdapter
import com.qmobile.qmobileapi.auth.isRemoteUrlValid
import com.qmobile.qmobiledatasync.toast.MessageType
import com.qmobile.qmobileui.utils.ToastHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TIMEOUT = 15

@BindingAdapter("downloadActivityAction")
fun downloadActivityAction(view: TextView, urlString: String?) {
    if (urlString.isNullOrEmpty()) return
    view.text = urlString

    if (!urlString.isRemoteUrlValid())
        return

    val okHttpClientBuilder = OkHttpClient().newBuilder()
        .connectTimeout(TIMEOUT.toLong(), TimeUnit.SECONDS)
        .readTimeout(TIMEOUT.toLong(), TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT.toLong(), TimeUnit.SECONDS)
    val okHttpClient = okHttpClientBuilder.build()

    view.setOnClickListener {

        val file = getFile(view.context, urlString)

        downloadFile(urlString, okHttpClient, file) { isSuccess ->
            if (isSuccess)
                startFileShareIntent(view.context, file.path)
            else
                ToastHelper.show(
                    view.context,
                    "Error while downloading file : $urlString",
                    MessageType.ERROR
                )
        }
    }
}

private fun getFile(context: Context, urlString: String): File {
    val fileName = try {
        urlString.removeSuffix("/").substring(urlString.lastIndexOf('/') + 1)
    } catch (e: StringIndexOutOfBoundsException) {
        Timber.e(e.localizedMessage)
        "downloadActivityAction_" + System.currentTimeMillis()
    }

    var file = File(context.getExternalFilesDir(DIRECTORY_DOWNLOADS), fileName)
    var i = 0
    while (file.exists()) {
        i++
        var newName = "${file.nameWithoutExtension.removeSuffix(" (${i - 1})")} ($i)"
        if (file.extension.isNotEmpty())
            newName += ".${file.extension}"
        file = File(context.getExternalFilesDir(DIRECTORY_DOWNLOADS), newName)
    }
    return file
}

private fun downloadFile(
    urlString: String,
    okHttpClient: OkHttpClient,
    file: File,
    onResult: (isSuccess: Boolean) -> Unit
) {

    val executor = Executors.newSingleThreadExecutor()
    val handler = Handler(Looper.getMainLooper())

    executor.execute {
        val request: Request = Request.Builder().url(urlString).build()
        val response: Response = okHttpClient.newCall(request).execute()
        val body: ResponseBody? = response.body
        val responseCode = response.code
        if (responseCode >= HttpURLConnection.HTTP_OK &&
            responseCode < HttpURLConnection.HTTP_MULT_CHOICE &&
            body != null
        ) {
            body.byteStream().apply {
                saveFileToExternalStorage(this, file)
            }
        }
        handler.post {
            onResult(
                responseCode >= HttpURLConnection.HTTP_OK &&
                    responseCode < HttpURLConnection.HTTP_MULT_CHOICE &&
                    body != null,

            )
        }
    }
}

fun saveFileToExternalStorage(inputStream: InputStream, target: File) {
    inputStream.use { input ->
        FileOutputStream(target).use { output ->
            input.copyTo(output)
        }
    }
}

private fun startFileShareIntent(context: Context, filePath: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val fileURI = FileProvider.getUriForFile(
            context, context.packageName + ".provider",
            File(filePath)
        )
        putExtra(Intent.EXTRA_STREAM, fileURI)
    }
    context.startActivity(shareIntent)
}
