package io.hyperswitch.networking

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object HyperNetworking {
    private val client = OkHttpClient()
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
                callback(Result.failure(e))
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
                    callback(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful ) {
                            callback(Result.failure(Exception("HTTP Error: ${it.code}")))
                        } else {
                            val responseBody = it.body?.string()
                            callback(Result.success(responseBody ?: "success"))
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