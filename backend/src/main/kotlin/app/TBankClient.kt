package app

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest

data class TBankConfig(
    val terminalKey: String,
    val password: String,
    val apiUrl: String,
    val notificationUrl: String,
    val successUrl: String,
    val failUrl: String,
) {
    val enabled: Boolean = terminalKey.isNotBlank() && password.isNotBlank()

    companion object {
        fun fromEnv(): TBankConfig = TBankConfig(
            terminalKey = env("TBANK_TERMINAL_KEY"),
            password = env("TBANK_PASSWORD"),
            apiUrl = env("TBANK_API_URL", "https://securepay.tinkoff.ru/v2"),
            notificationUrl = env("TBANK_NOTIFICATION_URL"),
            successUrl = env("TBANK_SUCCESS_URL"),
            failUrl = env("TBANK_FAIL_URL"),
        )

        private fun env(name: String, default: String = ""): String =
            System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
    }
}

data class TBankInitResult(
    val paymentId: Long,
    val paymentUrl: String,
    val status: String,
)

data class TBankNotification(
    val terminalKey: String,
    val orderId: String,
    val success: Boolean,
    val status: String,
    val paymentId: Long?,
    val amount: Long?,
    val errorCode: String?,
    val token: String,
    val raw: JsonObject,
)

class TBankClient(val config: TBankConfig) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun initPayment(
        orderId: String,
        amountRubles: Int,
        description: String,
    ): TBankInitResult {
        require(config.enabled) { "T-Bank is not configured" }
        val amountKopecks = amountRubles * 100
        val tokenParams = linkedMapOf(
            "TerminalKey" to config.terminalKey,
            "Amount" to amountKopecks.toString(),
            "OrderId" to orderId,
            "Description" to description.take(140),
        )
        if (config.notificationUrl.isNotBlank()) tokenParams["NotificationURL"] = config.notificationUrl
        if (config.successUrl.isNotBlank()) tokenParams["SuccessURL"] = config.successUrl
        if (config.failUrl.isNotBlank()) tokenParams["FailURL"] = config.failUrl

        val token = buildToken(tokenParams, config.password)
        val body = buildJsonObject {
            put("TerminalKey", config.terminalKey)
            put("Amount", amountKopecks)
            put("OrderId", orderId)
            put("Description", description.take(140))
            put("PayType", "O")
            put("Language", "ru")
            if (config.notificationUrl.isNotBlank()) put("NotificationURL", config.notificationUrl)
            if (config.successUrl.isNotBlank()) put("SuccessURL", config.successUrl)
            if (config.failUrl.isNotBlank()) put("FailURL", config.failUrl)
            put("Token", token)
        }

        val responseText = http.post("${config.apiUrl.trimEnd('/')}/Init") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()

        val response = json.parseToJsonElement(responseText).jsonObject
        val success = response["Success"]?.jsonPrimitive?.booleanOrNull == true
        if (!success) {
            val message = response["Message"]?.jsonPrimitive?.contentOrNull
                ?: response["Details"]?.jsonPrimitive?.contentOrNull
                ?: "T-Bank Init failed"
            val code = response["ErrorCode"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
            throw ApiException(
                io.ktor.http.HttpStatusCode.BadGateway,
                "PAYMENT_INIT_FAILED",
                "$code: $message",
            )
        }

        val paymentUrl = response["PaymentURL"]?.jsonPrimitive?.contentOrNull
            ?: throw ApiException(
                io.ktor.http.HttpStatusCode.BadGateway,
                "PAYMENT_INIT_FAILED",
                "T-Bank did not return PaymentURL",
            )
        val paymentId = response["PaymentId"]?.jsonPrimitive?.longOrNull
            ?: throw ApiException(
                io.ktor.http.HttpStatusCode.BadGateway,
                "PAYMENT_INIT_FAILED",
                "T-Bank did not return PaymentId",
            )
        val status = response["Status"]?.jsonPrimitive?.contentOrNull ?: "NEW"
        return TBankInitResult(paymentId = paymentId, paymentUrl = paymentUrl, status = status)
    }

    fun parseNotification(body: JsonObject): TBankNotification {
        val token = body["Token"]?.jsonPrimitive?.contentOrNull
            ?: throw ApiException(io.ktor.http.HttpStatusCode.BadRequest, "INVALID_NOTIFICATION", "Token is required")
        val params = body.entries
            .filter { (key, value) -> key != "Token" && value !is JsonObject && value !is kotlinx.serialization.json.JsonArray }
            .associate { (key, value) -> key to jsonPrimitiveValue(value) }
        if (!verifyToken(params, token)) {
            throw ApiException(io.ktor.http.HttpStatusCode.Forbidden, "INVALID_NOTIFICATION_TOKEN", "Invalid notification token")
        }
        return TBankNotification(
            terminalKey = params["TerminalKey"].orEmpty(),
            orderId = params["OrderId"].orEmpty(),
            success = params["Success"]?.equals("true", ignoreCase = true) == true,
            status = params["Status"].orEmpty(),
            paymentId = params["PaymentId"]?.toLongOrNull(),
            amount = params["Amount"]?.toLongOrNull(),
            errorCode = params["ErrorCode"],
            token = token,
            raw = body,
        )
    }

    private fun verifyToken(params: Map<String, String>, expectedToken: String): Boolean =
        buildToken(params, config.password).equals(expectedToken, ignoreCase = true)

    companion object {
        fun buildToken(params: Map<String, String>, password: String): String {
            val entries = params
                .filterValues { it.isNotBlank() }
                .map { it.key to it.value }
                .plus("Password" to password)
                .sortedBy { it.first }
            val payload = entries.joinToString("") { it.second }
            return sha256(payload)
        }

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(value.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }

        private fun jsonPrimitiveValue(element: JsonElement): String = when (element) {
            is JsonPrimitive -> when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.booleanOrNull.toString()
                element.longOrNull != null -> element.longOrNull.toString()
                else -> element.content
            }
            else -> element.toString()
        }
    }
}
