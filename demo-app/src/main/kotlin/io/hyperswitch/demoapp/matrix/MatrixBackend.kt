package io.hyperswitch.demoapp.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MatrixBackend(private val serverUrl: String) {

    data class IntentInfo(
        val sdkAuthorization: String,
        val paymentId: String,
        val publishableKey: String,
        val profileId: String,
        val amount: Int,
        val currency: String,
    )

    data class LedgerRow(
        val seq: Long,
        val method: String,
        val path: String,
        val authorization: String?,
        val fault: String?,
    )

    suspend fun createIntent(amount: Int, currency: String = "USD", customerId: String = "blah11111"): IntentInfo {
        val json = request("GET", "/create-payment-intent?amount=$amount&currency=$currency&customer_id=$customerId")
        return IntentInfo(
            sdkAuthorization = json.getString("sdkAuthorization"),
            paymentId = json.getString("paymentId"),
            publishableKey = json.getString("publishableKey"),
            profileId = json.optString("profileId"),
            amount = json.optInt("amount", amount),
            currency = json.optString("currency", currency),
        )
    }

    suspend fun updatePayment(paymentId: String, amount: Int, currency: String = "USD"): String =
        request(
            "POST",
            "/update-payment",
            """{"paymentId":"$paymentId","amount":$amount,"currency":"$currency"}""",
        ).getString("sdkAuthorization")

    suspend fun ledger(since: Long): Pair<List<LedgerRow>, Long> {
        val json = request("GET", "/ledger?since=$since")
        val rows = json.getJSONArray("rows")
        val list = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            LedgerRow(
                seq = row.getLong("seq"),
                method = row.getString("method"),
                path = row.getString("path"),
                authorization = if (row.isNull("authorization")) null else row.getString("authorization"),
                fault = if (row.isNull("fault")) null else row.getString("fault"),
            )
        }
        return list to json.getLong("last")
    }

    suspend fun ledgerLast(): Long = request("GET", "/ledger/last").getLong("last")

    suspend fun clearLedger() {
        request("DELETE", "/ledger")
    }

    suspend fun addFault(
        pathContains: String,
        authorization: String,
        mode: String,
        statusOrDelayMs: Int,
        times: Int = 1,
    ): Int {
        val body = JSONObject().apply {
            put("pathContains", pathContains)
            put("authorization", authorization)
            put("mode", mode)
            if (mode == "error") put("status", statusOrDelayMs) else put("delayMs", statusOrDelayMs)
            put("times", times)
        }.toString()
        return request("POST", "/proxy-control", body).getInt("id")
    }

    suspend fun clearFaults() {
        request("DELETE", "/proxy-control")
    }

    private suspend fun request(method: String, path: String, body: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = (URL(serverUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json")
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (status !in 200..299) throw IllegalStateException("$method $path -> HTTP $status: $text")
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
}
