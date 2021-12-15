package ___PACKAGE___

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.databinding.BindingAdapter
import com.qmobile.qmobiledatasync.toast.MessageType
import com.qmobile.qmobileui.utils.ToastHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TIMEOUT = 15
private const val BUFFER_LENGTH_BYTES = 8 * 1024

@BindingAdapter("downloadActivityAction")
fun downloadActivityAction(view: TextView, urlString: String?) {
    if (urlString.isNullOrEmpty()) return

    val okHttpClientBuilder = OkHttpClient().newBuilder()
        .connectTimeout(TIMEOUT.toLong(), TimeUnit.SECONDS)
        .readTimeout(TIMEOUT.toLong(), TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT.toLong(), TimeUnit.SECONDS)
    val okHttpClient = okHttpClientBuilder.build()

    view.setOnClickListener {

        val fileName = "downloadActivityAction_" + System.currentTimeMillis()
        val file = File(view.context.cacheDir, fileName)

        downloadFile(urlString, okHttpClient, file) { isSuccess ->
            if (isSuccess)
                startFileShareIntent(view.context, file.path)
            else
                ToastHelper.show(
                    view.context,
                    "Error while downloading data file : $urlString",
                    MessageType.ERROR
                )
        }
    }
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
                file.outputStream().use { fileOut ->
                    var bytesCopied = 0
                    val buffer = ByteArray(BUFFER_LENGTH_BYTES)
                    var bytes = read(buffer)
                    while (bytes >= 0) {
                        fileOut.write(buffer, 0, bytes)
                        bytesCopied += bytes
                        bytes = read(buffer)
                    }
                }
            }
        }
        handler.post {
            onResult(
                responseCode >= HttpURLConnection.HTTP_OK &&
                        responseCode < HttpURLConnection.HTTP_MULT_CHOICE &&
                        body != null
            )
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
