package io.hyperswitch.networking

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection


object HyperNetworking {

    private suspend fun makeHttpRequest(
        urlString: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = method
                    doOutput = body != null
                    headers.forEach { (key, value) -> setRequestProperty(key, value) }
                }

                body?.let {
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(it)
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    "Error: $responseCode"
                }

                connection.disconnect()
                response
            } catch (e: Exception) {
                Log.e("HyperNetworking", "Request failed: ${e.message}")
                "Exception: ${e.message}"
            }
        }
    }
    suspend fun makePostRequest(urlString: String, postData: Any): String {
        return makeHttpRequest(
            urlString = urlString,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = postData.toString()
        )
    }
}