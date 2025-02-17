package io.hyperswitch.networking

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection


object HyperNetworking {
    fun makeHttpRequest(
        urlString: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): String {
        var response = ""
        val thread = Thread {
            try {

                val url = URL(urlString)


                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = method // Set the HTTP method
                connection.doOutput = body != null // Allow sending data if body is not null


                headers.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }


                if (body != null) {
                    val outputStream = OutputStreamWriter(connection.outputStream)
                    outputStream.write(body)
                    outputStream.flush()
                    outputStream.close()
                }

                val responseCode = connection.responseCode
                println("Response Code: $responseCode")
                response = if (responseCode == HttpURLConnection.HTTP_OK) {

                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    "Error: $responseCode"
                }


                connection.disconnect()
            } catch (e: Exception) {
                response = "Exception: ${e.message}"
                println("Error: ${e.message}")
            }
        }

        thread.start()
        thread.join()
        return response
    }

    fun makePostRequest(urlString: String, postData: String): String {
        return makeHttpRequest(
            urlString = urlString,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = postData
        )
    }

    fun makePostRequest(urlString: String, stringArray: List<String>): String {
        val postData = stringArray.toString() // Serialize array to JSON
        return makeHttpRequest(
            urlString = urlString,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            body = postData
        )
    }


}

