package io.hyperswitch.networking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.KeyManagerFactory
import java.security.KeyStore

object HyperNetworking {

    private fun getSSLContext(): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
            }
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, null)
            }
            init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, null)
        }
    }

    private suspend fun makeHttpRequest(
        urlString: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                (url.openConnection() as? HttpsURLConnection)?.run {
                    sslSocketFactory = getSSLContext().socketFactory
                    requestMethod = method
                    doOutput = body != null
                    connectTimeout = 15000
                    readTimeout = 15000
                    headers.forEach { (key, value) -> setRequestProperty(key, value) }
                    body?.let {
                        outputStream.use { output ->
                            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                                writer.write(it)
                                writer.flush()
                            }
                        }
                    }
                    val responseCode = responseCode
                    val response = if (responseCode in 200..299) {
                        inputStream.bufferedReader().use(BufferedReader::readText)
                    } else {
                        errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "Error: $responseCode"
                    }
                    disconnect()
                    Result.success(response)
                } ?: Result.failure(Exception("Failed to open HTTPS connection"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun makePostRequest(urlString: String, postData: Any): Result<String> {
        return makeHttpRequest(
            urlString = urlString,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = postData.toString()
        )
    }
}