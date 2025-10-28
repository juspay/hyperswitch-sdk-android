package io.hyperswitch.networking

import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object HyperNetworking {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun makeHttpRequest(
        urlString: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        callback: (Result<String>) -> Unit
    ) {
        try {
            val mediaType = "application/json".toMediaType()
            val requestBody = body?.takeIf { it.isNotBlank() }?.toRequestBody(mediaType)

            val url = try {
                urlString.toHttpUrl()
            } catch (e: Exception) {
                callback(Result.failure(IllegalArgumentException("Invalid URL: $urlString", e)))
                return
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .method(method, requestBody)

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(Result.failure(IOException("Network error: ${e.localizedMessage}", e)))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        when {
                            it.isSuccessful -> callback(Result.success(it.body?.string() ?: "success"))
                            it.code == 401 -> callback(Result.failure(Exception("Unauthorized (401) - Check API Key")))
                            it.code == 500 -> callback(Result.failure(Exception("Server Error (500) - Try again later")))
                            else -> callback(Result.failure(Exception("HTTP Error: ${it.code} - ${it.message}")))
                        }
                    }
                }
            })
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    fun makePostRequest(urlString: String, postData: Any, callback: (Result<String>) -> Unit) {
        makeHttpRequest(
            urlString = urlString,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = postData.toString().takeIf { it != "null" },
            callback = callback
        )
    }
}