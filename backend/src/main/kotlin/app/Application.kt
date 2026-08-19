@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.postgresql.util.PGobject
import org.slf4j.LoggerFactory
import java.net.IDN
import java.net.URI
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

private val MoscowZone: ZoneId = ZoneId.of("Europe/Moscow")
private val paymentLogger = LoggerFactory.getLogger("app.payment")

private fun expandCorsOrigins(origins: List<String>): List<String> =
    origins.flatMap { origin ->
        if (origin == "*") return@flatMap listOf(origin)
        runCatching {
            val uri = URI(origin)
            val host = uri.host ?: return@flatMap listOf(origin)
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val scheme = uri.scheme
            val hosts = linkedSetOf(host, IDN.toASCII(host, IDN.ALLOW_UNASSIGNED), IDN.toUnicode(host))
            hosts.map { h -> "$scheme://$h$port" }
        }.getOrElse { listOf(origin) }
    }.distinct()

private val appJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

fun main() {
    val config = AppConfig.fromEnv()
    val database = AppDatabase(config)
    embeddedServer(Netty, port = config.serverPort, host = "0.0.0.0") {
        module(config, database)
    }.start(wait = true)
}

fun Application.module(
    config: AppConfig = AppConfig.fromEnv(),
    database: AppDatabase = AppDatabase(config),
    tbankClient: TBankClient = TBankClient(TBankConfig.fromEnv()),
) {
    val appLog = environment.log
    install(SimpleRateLimitPlugin)
    install(ContentNegotiation) {
        json(appJson)
    }
    install(CallLogging)
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.error, cause.message ?: cause.error))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid request"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", cause.message ?: "Invalid request"))
        }
        exception<Throwable> { call, cause ->
            appLog.error("Unhandled request failure", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Internal server error"))
        }
    }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Device-Id")
        expandCorsOrigins(config.corsAllowedOrigins).forEach { origin ->
            if (origin == "*") {
                anyHost()
            } else {
                val uri = URI(origin)
                val port = if (uri.port > 0) ":${uri.port}" else ""
                allowHost("${uri.host}$port", schemes = listOf(uri.scheme))
            }
        }
    }
    install(Authentication) {
        bearer("api-bearer") {
            authenticate { credentials ->
                if (credentials.token == config.bearerToken) UserIdPrincipal("api") else null
            }
        }
    }

    routing {
        route("/api") {
            publicRoutes(config, database, tbankClient)
            authenticate("api-bearer") {
                kitchenRoutes(database)
                adminRoutes(database)
            }
        }
    }
}

data class AppConfig(
    val databaseUrl: String,
    val postgresUser: String,
    val postgresPassword: String,
    val bearerToken: String,
    val serverPort: Int,
    val corsAllowedOrigins: List<String>,
    val paymentSkip: Boolean,
) {
    companion object {
        fun fromEnv(): AppConfig = AppConfig(
            databaseUrl = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/shaurma"),
            postgresUser = env("POSTGRES_USER", "shaurma"),
            postgresPassword = env("POSTGRES_PASSWORD", "change_me"),
            bearerToken = env("BEARER_TOKEN", "change_me"),
            serverPort = env("SERVER_PORT", "8080").toInt(),
            corsAllowedOrigins = env("CORS_ALLOWED_ORIGINS", "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            paymentSkip = env("PAYMENT_SKIP", "false").equals("true", ignoreCase = true),
        )

        private fun env(name: String, default: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
    }
}

class AppDatabase(config: AppConfig) {
    val dataSource: HikariDataSource

    init {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            username = config.postgresUser
            password = config.postgresPassword
            maximumPoolSize = 10
            minimumIdle = 1
            poolName = "shaurma-backend"
        }
        dataSource = HikariDataSource(hikari)
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        Database.connect(dataSource)
    }

    fun <T> read(block: (Connection) -> T): T =
        dataSource.connection.use { connection -> block(connection) }

    fun <T> transaction(block: (Connection, Long) -> T): T =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            val now = serverNow(connection)
            try {
                val result = block(connection, now)
                connection.commit()
                result
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
}

class ApiException(
    val status: HttpStatusCode,
    val error: String,
    override val message: String,
) : RuntimeException(message)

@Serializable data class ErrorResponse(val error: String, val message: String)
@Serializable data class InitRequest(val deviceId: String, val platform: String)
@Serializable data class InitResponse(
    val clientNumber: Int,
    val name: String? = null,
    val phone: String? = null,
    val isBlocked: Boolean,
    val serverTime: Long,
)
@Serializable data class MenuResponse(val serverTime: Long, val categories: List<CategoryDto>)
@Serializable data class DayScheduleDto(
    val dayOfWeek: Int,
    val openTime: String,
    val lastOrderTime: String,
)
@Serializable data class PublicConfigResponse(
    val serverTime: Long,
    val workStartTime: String,
    val cutoffTime: String,
    val isOpen: Boolean,
    val paymentEnabled: Boolean,
    val weeklySchedule: List<DayScheduleDto> = emptyList(),
)
@Serializable data class CategoryDto(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val isGrill: Boolean,
    val defaultCookingMinutes: Int = 15,
    val items: List<MenuItemDto> = emptyList(),
)
@Serializable data class MenuItemDto(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val description: String? = null,
    val price: Int,
    val weight: Int,
    val cookingTime: Int,
    val imageUrl: String? = null,
    val sortOrder: Int,
    val isActive: Boolean,
    val additions: List<AdditionDto> = emptyList(),
    val removals: List<RemovalDto> = emptyList(),
)
@Serializable data class AdditionDto(
    val id: Long,
    val menuItemId: Long,
    val name: String,
    val price: Int,
    val weight: Int,
    val isActive: Boolean,
)
@Serializable data class RemovalDto(
    val id: Long,
    val menuItemId: Long,
    val name: String,
    val isActive: Boolean,
)
@Serializable data class CreateOrderRequest(
    val requestedTime: Long,
    val generalComment: String? = null,
    val items: List<CreateOrderItemRequest>,
)
@Serializable data class CreateOrderItemRequest(
    val menuItemId: Long,
    val additionsIds: List<Long> = emptyList(),
    val removalsIds: List<Long> = emptyList(),
)
@Serializable data class CreateOrderResponse(
    val publicId: String,
    val status: String,
    val updatedAt: Long,
    val paymentStatus: String,
    val paymentUrl: String? = null,
)

@Serializable data class PaymentStatusResponse(
    val publicId: String,
    val paymentStatus: String,
    val status: String,
)
@Serializable data class OrdersResponse(val orders: List<OrderDto>)
@Serializable data class KitchenOrdersResponse(val orders: List<KitchenOrderDto>)
@Serializable data class OrderDto(
    val id: Long,
    val publicId: String,
    val status: String,
    val paymentStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
    val requestedTime: Long,
    val cookingStartTime: Long,
    val totalPrice: Int,
    val generalComment: String? = null,
    val items: List<OrderItemDto>,
)
@Serializable data class PendingOrderResponse(val order: OrderDto? = null)
@Serializable data class KitchenOrderDto(
    val id: Long,
    val publicId: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val requestedTime: Long,
    val cookingStartTime: Long,
    val totalPrice: Int,
    val generalComment: String? = null,
    val items: List<OrderItemDto>,
    val client: ClientDto,
)
@Serializable data class OrderItemDto(
    val id: Long,
    val menuItemId: Long,
    val nameSnapshot: String,
    val priceSnapshot: Int,
    val weightSnapshot: Int,
    val additionsSnapshot: List<AdditionSnapshot>,
    val removalsSnapshot: List<RemovalSnapshot>,
)
@Serializable data class AdditionSnapshot(val id: Long, val name: String, val price: Int, val weight: Int)
@Serializable data class RemovalSnapshot(val id: Long, val name: String)
@Serializable data class ClientDto(
    val deviceId: String,
    val clientNumber: Int,
    val name: String? = null,
    val phone: String? = null,
    val isBlocked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
@Serializable data class UpdateStatusRequest(val status: String)
@Serializable data class ClientStatusRequest(val isBlocked: Boolean)
@Serializable data class StatsBucket(val totalSum: Long, val orderCount: Long)
@Serializable data class StatisticsResponse(val today: StatsBucket, val period: StatsBucket)
@Serializable data class SettingDto(val key: String, val value: String, val updatedAt: Long)
@Serializable data class SettingsResponse(
    val settings: List<SettingDto>,
    val weeklySchedule: List<DayScheduleDto> = emptyList(),
)

@Serializable data class CategoryUpsert(
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val isGrill: Boolean = false,
    val defaultCookingMinutes: Int = 15,
)
@Serializable data class MenuItemUpsert(
    val categoryId: Long,
    val name: String,
    val description: String? = null,
    val price: Int,
    val weight: Int,
    val cookingTime: Int,
    val imageUrl: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)
@Serializable data class AdditionUpsert(
    val menuItemId: Long,
    val name: String,
    val price: Int,
    val weight: Int,
    val isActive: Boolean = true,
)
@Serializable data class RemovalUpsert(
    val menuItemId: Long,
    val name: String,
    val isActive: Boolean = true,
)

private data class DeviceRow(
    val deviceId: UUID,
    val clientNumber: Int,
    val name: String?,
    val phone: String?,
    val isBlocked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class MenuItemRow(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val description: String?,
    val price: Int,
    val weight: Int,
    val cookingTime: Int,
    val imageUrl: String?,
    val sortOrder: Int,
    val isActive: Boolean,
    val isGrill: Boolean,
)

private data class OrderRow(
    val id: Long,
    val publicId: String,
    val deviceId: UUID,
    val status: String,
    val paymentStatus: String,
    val tbankPaymentId: Long?,
    val paidAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val requestedTime: Long,
    val cookingStartTime: Long,
    val totalPrice: Int,
    val generalComment: String?,
    val client: ClientDto? = null,
)

private data class CreatedOrder(
    val id: Long,
    val publicId: String,
    val totalPrice: Int,
    val updatedAt: Long,
)

private data class Settings(val workStart: LocalTime, val cutoffRegular: LocalTime, val cutoffGrill: LocalTime)
private data class DaySchedule(val openTime: LocalTime, val lastOrderTime: LocalTime)
private data class BuiltOrderItem(
    val menuItemId: Long,
    val name: String,
    val price: Int,
    val weight: Int,
    val cookingTime: Int,
    val isGrill: Boolean,
    val additions: List<AdditionSnapshot>,
    val removals: List<RemovalSnapshot>,
) {
    val totalPrice: Int = price + additions.sumOf { it.price }
}

private data class RateBucket(var minute: Long, var count: Int)

private class SimpleRateLimiter(private val limitPerMinute: Int = 300) {
    private val buckets = ConcurrentHashMap<String, RateBucket>()

    fun allow(call: io.ktor.server.application.ApplicationCall): Boolean {
        val nowMinute = System.currentTimeMillis() / 60_000L
        val forwardedFor = call.request.headers["X-Forwarded-For"]?.substringBefore(",")?.trim()
        val ip = forwardedFor?.takeIf { it.isNotEmpty() }
            ?: call.request.headers["X-Real-IP"]
            ?: "unknown"
        val path = call.request.path()
        val key = "$ip:$path"
        val bucket = buckets.compute(key) { _, old ->
            if (old == null || old.minute != nowMinute) RateBucket(nowMinute, 1) else old.apply { count += 1 }
        }
        return (bucket?.count ?: 0) <= limitPerMinute
    }
}

private val SimpleRateLimitPlugin = createApplicationPlugin(name = "SimpleRateLimit") {
    val limiter = SimpleRateLimiter()
    onCall { call ->
        if (!limiter.allow(call)) {
            throw ApiException(HttpStatusCode.TooManyRequests, "RATE_LIMITED", "Too many requests")
        }
    }
}

private fun Route.publicRoutes(config: AppConfig, database: AppDatabase, tbankClient: TBankClient) {
    post("/init") {
        val request = call.receive<InitRequest>()
        val platform = parseClientPlatform(request.platform)
        val deviceId = parseUuid(request.deviceId)
        val response = database.transaction { connection, now ->
            val device = findOrCreateDevice(connection, deviceId, platform, now)
            device.toInitResponse(now)
        }
        call.respond(response)
    }

    get("/menu") {
        val response = database.read { connection ->
            MenuResponse(serverTime = serverNow(connection), categories = readMenu(connection, onlyActive = true))
        }
        call.respond(response)
    }

    get("/config") {
        val response = database.read { connection ->
            val now = serverNow(connection)
            val schedule = readWeeklySchedule(connection)
            val scheduleDtos = schedule.toDtoList()
            val todaySchedule = schedule.forDate(Instant.ofEpochMilli(now).atZone(MoscowZone).toLocalDate())
            val nowTime = Instant.ofEpochMilli(now).atZone(MoscowZone).toLocalTime()
            PublicConfigResponse(
                serverTime = now,
                workStartTime = todaySchedule.openTime.toString(),
                cutoffTime = todaySchedule.lastOrderTime.toString(),
                isOpen = !nowTime.isBefore(todaySchedule.openTime) && !nowTime.isAfter(todaySchedule.lastOrderTime),
                paymentEnabled = tbankClient.config.enabled,
                weeklySchedule = scheduleDtos,
            )
        }
        call.respond(response)
    }

    post("/profile") {
        val body = call.receive<JsonObject>()
        val response = database.transaction { connection, now ->
            val device = requireDevice(connection, call.deviceIdHeader())
            val newName = if ("name" in body) parseNullableString(body["name"], "name")?.trim()?.also {
                if (it.length > 30) throw ApiException(HttpStatusCode.BadRequest, "INVALID_NAME", "Name must be at most 30 characters")
            } else device.name
            val newPhone = if ("phone" in body) parseNullableString(body["phone"], "phone")?.trim()?.also {
                if (!Regex("""^\+7\d{10}$""").matches(it)) {
                    throw ApiException(HttpStatusCode.BadRequest, "INVALID_PHONE", "Phone must match +7XXXXXXXXXX")
                }
            } else device.phone
            val changed = newName != device.name || newPhone != device.phone
            if (changed) {
                connection.prepareStatement("UPDATE devices SET name = ?, phone = ?, updated_at = ? WHERE device_id = ?").use { statement ->
                    statement.setNullableString(1, newName)
                    statement.setNullableString(2, newPhone)
                    statement.setLong(3, now)
                    statement.setObject(4, device.deviceId)
                    statement.executeUpdate()
                }
            }
            (if (changed) requireDevice(connection, device.deviceId) else device).toInitResponse(now)
        }
        call.respond(response)
    }

    post("/order") {
        if (!tbankClient.config.enabled && !config.paymentSkip) {
            throw ApiException(
                HttpStatusCode.ServiceUnavailable,
                "PAYMENT_NOT_CONFIGURED",
                "Онлайн-оплата не настроена. Укажите TBANK_TERMINAL_KEY и TBANK_PASSWORD в .env и перезапустите backend.",
            )
        }
        val request = call.receive<CreateOrderRequest>()
        val deviceId = call.deviceIdHeader()
        val paymentRequired = tbankClient.config.enabled
        val created = database.transaction { connection, now ->
            createOrderDraft(connection, deviceId, request, now, paymentRequired)
        }
        call.respond(
            CreateOrderResponse(
                publicId = created.publicId,
                status = "NEW",
                updatedAt = created.updatedAt,
                paymentStatus = if (paymentRequired) "WAITING" else "PAID",
                paymentUrl = null,
            )
        )
    }

    get("/order/pending") {
        val deviceId = call.deviceIdHeader()
        val response = database.read { connection ->
            val order = findUnpaidOrderByDevice(connection, deviceId)
            PendingOrderResponse(order = order?.toOrderDto(readOrderItems(connection, order.id)))
        }
        call.respond(response)
    }

    get("/order/{public_id}/payment") {
        val publicId = call.parameters["public_id"]
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "public_id is required")
        val deviceId = call.deviceIdHeader()
        val response = database.read { connection ->
            val order = findOrderByPublicId(connection, publicId)
                ?: throw ApiException(HttpStatusCode.NotFound, "ORDER_NOT_FOUND", "Order not found")
            if (order.deviceId != deviceId) {
                throw ApiException(HttpStatusCode.Forbidden, "ORDER_FORBIDDEN", "Order does not belong to this device")
            }
            PaymentStatusResponse(
                publicId = order.publicId,
                paymentStatus = order.paymentStatus,
                status = order.status,
            )
        }
        call.respond(response)
    }

    post("/order/{public_id}/pay") {
        val publicId = call.parameters["public_id"]
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "public_id is required")
        val deviceId = call.deviceIdHeader()
        val created = database.read { connection ->
            val order = findOrderByPublicId(connection, publicId)
                ?: throw ApiException(HttpStatusCode.NotFound, "ORDER_NOT_FOUND", "Order not found")
            if (order.deviceId != deviceId) {
                throw ApiException(HttpStatusCode.Forbidden, "ORDER_FORBIDDEN", "Order does not belong to this device")
            }
            if (order.paymentStatus == "PAID") {
                throw ApiException(HttpStatusCode.BadRequest, "ALREADY_PAID", "Order is already paid")
            }
            if (order.paymentStatus == "CANCELLED") {
                throw ApiException(HttpStatusCode.BadRequest, "ORDER_CANCELLED", "Order is cancelled")
            }
            CreatedOrder(
                id = order.id,
                publicId = order.publicId,
                totalPrice = order.totalPrice,
                updatedAt = order.updatedAt,
            )
        }
        if (!tbankClient.config.enabled && !config.paymentSkip) {
            throw ApiException(
                HttpStatusCode.ServiceUnavailable,
                "PAYMENT_NOT_CONFIGURED",
                "Онлайн-оплата не настроена. Укажите TBANK_TERMINAL_KEY и TBANK_PASSWORD в .env и перезапустите backend.",
            )
        }
        val payment = initiateOrderPayment(config, tbankClient, database, created)
        call.respond(
            CreateOrderResponse(
                publicId = created.publicId,
                status = "NEW",
                updatedAt = payment.updatedAt,
                paymentStatus = payment.paymentStatus,
                paymentUrl = payment.paymentUrl,
            )
        )
    }

    post("/order/{public_id}/cancel") {
        val publicId = call.parameters["public_id"]
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "public_id is required")
        val deviceId = call.deviceIdHeader()
        val cancelled = database.transaction { connection, now ->
            val order = findOrderByPublicId(connection, publicId)
                ?: throw ApiException(HttpStatusCode.NotFound, "ORDER_NOT_FOUND", "Order not found")
            if (order.deviceId != deviceId) {
                throw ApiException(HttpStatusCode.Forbidden, "ORDER_FORBIDDEN", "Order does not belong to this device")
            }
            if (order.paymentStatus == "PAID") {
                throw ApiException(HttpStatusCode.BadRequest, "ALREADY_PAID", "Order is already paid")
            }
            if (order.paymentStatus == "CANCELLED") {
                throw ApiException(HttpStatusCode.BadRequest, "ALREADY_CANCELLED", "Order is already cancelled")
            }
            markOrderCancelled(connection, order.id, now)
            findOrderByPublicId(connection, publicId)!!
                .toOrderDto(readOrderItems(connection, order.id))
        }
        call.respond(cancelled)
    }

    post("/webhooks/tbank") {
        val body = call.receive<JsonObject>()
        val notification = tbankClient.parseNotification(body)
        handleTBankNotification(tbankClient, database, notification)
        call.respondText("OK")
    }

    get("/orders/my") {
        val sinceUpdatedAt = call.request.queryParameters["since_updated_at"]?.toLongOrNull() ?: 0L
        val sinceId = call.request.queryParameters["since_id"]?.toLongOrNull() ?: 0L
        val response = database.read { connection ->
            val device = requireDevice(connection, call.deviceIdHeader())
            OrdersResponse(readOrdersForDevice(connection, device.deviceId, sinceUpdatedAt, sinceId))
        }
        call.respond(response)
    }
}

private fun Route.kitchenRoutes(database: AppDatabase) {
    get("/orders") {
        val sinceUpdatedAt = call.request.queryParameters["since_updated_at"]?.toLongOrNull() ?: 0L
        val sinceId = call.request.queryParameters["since_id"]?.toLongOrNull() ?: 0L
        val response = database.read { connection ->
            KitchenOrdersResponse(readKitchenOrders(connection, sinceUpdatedAt, sinceId))
        }
        call.respond(response)
    }

    post("/order/{public_id}/status") {
        val publicId = call.parameters["public_id"] ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "public_id is required")
        val request = call.receive<UpdateStatusRequest>()
        val response = database.transaction { connection, now ->
            updateOrderStatus(connection, publicId, request.status, now)
        }
        call.respond(response)
    }

    get("/clients") {
        val search = call.request.queryParameters["search"]?.trim()?.takeIf { it.isNotEmpty() }
        val clients = database.read { connection -> readClients(connection, search) }
        call.respond(mapOf("clients" to clients))
    }

    put("/clients/{device_id}/status") {
        val deviceId = parseUuid(call.parameters["device_id"] ?: "")
        val request = call.receive<ClientStatusRequest>()
        val client = database.transaction { connection, now ->
            val updated = connection.prepareStatement(
                "UPDATE devices SET is_blocked = ?, updated_at = ? WHERE device_id = ?"
            ).use { statement ->
                statement.setBoolean(1, request.isBlocked)
                statement.setLong(2, now)
                statement.setObject(3, deviceId)
                statement.executeUpdate()
            }
            if (updated == 0) throw deviceNotFound()
            requireDevice(connection, deviceId).toClientDto()
        }
        call.respond(client)
    }

    get("/clients/{client_number}/orders") {
        val clientNumber = call.parameters["client_number"]?.toIntOrNull()
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "client_number must be an integer")
        val response = database.read { connection ->
            val device = findDeviceByClientNumber(connection, clientNumber)
                ?: throw ApiException(HttpStatusCode.NotFound, "CLIENT_NOT_FOUND", "Client not found")
            OrdersResponse(readOrdersForDevice(connection, device.deviceId, 0, 0))
        }
        call.respond(response)
    }

    get("/statistics") {
        val response = database.read { connection ->
            val nowDate = Instant.ofEpochMilli(serverNow(connection)).atZone(MoscowZone).toLocalDate()
            val from = call.request.queryParameters["from"]?.let { LocalDate.parse(it) } ?: nowDate
            val to = call.request.queryParameters["to"]?.let { LocalDate.parse(it) } ?: from
            if (to.isBefore(from)) throw ApiException(HttpStatusCode.BadRequest, "INVALID_PERIOD", "to must be after from")
            StatisticsResponse(
                today = readStats(connection, nowDate, nowDate),
                period = readStats(connection, from, to),
            )
        }
        call.respond(response)
    }
}

private fun Route.adminRoutes(database: AppDatabase) {
    route("/admin/categories") {
        get { call.respond(database.read { readMenu(it, onlyActive = false) }) }
        post {
            val request = call.receive<CategoryUpsert>().validate()
            val created = database.transaction { connection, _ -> createCategory(connection, request) }
            call.respond(created)
        }
    }
    route("/admin/categories/{id}") {
        put {
            val id = call.pathId()
            val request = call.receive<CategoryUpsert>().validate()
            val updated = database.transaction { connection, _ -> updateCategory(connection, id, request) }
            call.respond(updated)
        }
        delete {
            val id = call.pathId()
            val updated = database.transaction { connection, _ -> softDelete(connection, "categories", id) }
            call.respond(updated)
        }
    }

    route("/admin/menu_items") {
        get { call.respond(database.read { readAdminMenuItems(it) }) }
        post {
            val request = call.receive<MenuItemUpsert>().validate()
            val created = database.transaction { connection, now -> createMenuItem(connection, request, now) }
            call.respond(created)
        }
    }
    route("/admin/menu_items/{id}") {
        put {
            val id = call.pathId()
            val request = call.receive<MenuItemUpsert>().validate()
            val updated = database.transaction { connection, now -> updateMenuItem(connection, id, request, now) }
            call.respond(updated)
        }
        delete {
            val id = call.pathId()
            val updated = database.transaction { connection, now -> softDeleteMenuItem(connection, id, now) }
            call.respond(updated)
        }
    }

    route("/admin/additions") {
        get { call.respond(database.read { readAdditions(it, onlyActive = false) }) }
        post {
            val request = call.receive<AdditionUpsert>().validate()
            val created = database.transaction { connection, _ -> createAddition(connection, request) }
            call.respond(created)
        }
    }
    route("/admin/additions/{id}") {
        put {
            val id = call.pathId()
            val request = call.receive<AdditionUpsert>().validate()
            val updated = database.transaction { connection, _ -> updateAddition(connection, id, request) }
            call.respond(updated)
        }
        delete {
            val id = call.pathId()
            val updated = database.transaction { connection, _ -> softDelete(connection, "additions", id) }
            call.respond(updated)
        }
    }

    route("/admin/removals") {
        get { call.respond(database.read { readRemovals(it, onlyActive = false) }) }
        post {
            val request = call.receive<RemovalUpsert>().validate()
            val created = database.transaction { connection, _ -> createRemoval(connection, request) }
            call.respond(created)
        }
    }
    route("/admin/removals/{id}") {
        put {
            val id = call.pathId()
            val request = call.receive<RemovalUpsert>().validate()
            val updated = database.transaction { connection, _ -> updateRemoval(connection, id, request) }
            call.respond(updated)
        }
        delete {
            val id = call.pathId()
            val updated = database.transaction { connection, _ -> softDelete(connection, "removals", id) }
            call.respond(updated)
        }
    }

    route("/admin/settings") {
        get {
            val response = database.read { connection ->
                SettingsResponse(
                    settings = readSettingsDtos(connection),
                    weeklySchedule = readWeeklySchedule(connection).toDtoList(),
                )
            }
            call.respond(response)
        }
        put {
            val body = call.receive<JsonObject>()
            val response = database.transaction { connection, now ->
                val settings = updateSettings(connection, body, now)
                if ("weekly_schedule" in body) {
                    updateWeeklySchedule(connection, parseWeeklySchedule(body["weekly_schedule"]), now)
                }
                SettingsResponse(
                    settings = settings,
                    weeklySchedule = readWeeklySchedule(connection).toDtoList(),
                )
            }
            call.respond(response)
        }
    }
}

private val supportedClientPlatforms = setOf("android", "ios", "web")

private fun parseClientPlatform(value: String): String {
    val platform = value.trim().lowercase()
    if (platform !in supportedClientPlatforms) {
        throw ApiException(
            HttpStatusCode.BadRequest,
            "INVALID_PLATFORM",
            "Platform must be one of: ${supportedClientPlatforms.joinToString(", ")}",
        )
    }
    return platform
}

private fun findOrCreateDevice(connection: Connection, deviceId: UUID, platform: String, now: Long): DeviceRow {
    findDevice(connection, deviceId)?.let { return it }
    connection.createStatement().use { it.execute("LOCK TABLE devices IN EXCLUSIVE MODE") }
    findDevice(connection, deviceId)?.let { return it }
    val nextNumber = connection.prepareStatement(
        "SELECT COALESCE(MAX(client_number), 10000) + 1 FROM devices WHERE client_number BETWEEN 10001 AND 59999"
    ).use { statement ->
        statement.executeQuery().use { result ->
            result.next()
            result.getInt(1)
        }
    }
    if (nextNumber > 59999) {
        throw ApiException(HttpStatusCode.Conflict, "CLIENT_NUMBER_EXHAUSTED", "Client number range is exhausted")
    }
    connection.prepareStatement(
        """
        INSERT INTO devices (device_id, platform, client_number, is_blocked, created_at, updated_at)
        VALUES (?, ?, ?, false, ?, ?)
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, deviceId)
        statement.setString(2, platform)
        statement.setInt(3, nextNumber)
        statement.setLong(4, now)
        statement.setLong(5, now)
        statement.executeUpdate()
    }
    return requireDevice(connection, deviceId)
}

private fun createOrderDraft(
    connection: Connection,
    deviceId: UUID,
    request: CreateOrderRequest,
    now: Long,
    paymentRequired: Boolean,
): CreatedOrder {
    val device = requireDevice(connection, deviceId)
    if (device.isBlocked) throw ApiException(HttpStatusCode.Forbidden, "DEVICE_BLOCKED", "Device is blocked")
    findUnpaidOrderByDevice(connection, deviceId)?.let { unpaid ->
        throw ApiException(
            HttpStatusCode.Conflict,
            "UNPAID_ORDER_EXISTS",
            "У вас уже есть неоплаченный заказ ${unpaid.publicId}. Оплатите или отмените его в разделе «Мои заказы».",
        )
    }
    if (request.items.isEmpty() || request.items.size > 50) {
        throw ApiException(HttpStatusCode.BadRequest, "INVALID_ITEMS", "Order must contain 1..50 items")
    }
    request.items.forEach {
        if (it.additionsIds.size > 20 || it.removalsIds.size > 20) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_ITEMS", "Each item can contain at most 20 additions and removals")
        }
        if (it.additionsIds.size != it.additionsIds.toSet().size || it.removalsIds.size != it.removalsIds.toSet().size) {
            throw ApiException(HttpStatusCode.BadRequest, "INVALID_ITEMS", "Duplicate additions or removals are not allowed")
        }
    }

    val builtItems = request.items.map { buildOrderItem(connection, it) }
    val maxCookingMinutes = builtItems.maxOf { it.cookingTime }
    val schedule = readWeeklySchedule(connection)
    validateRequestedTime(request.requestedTime, now, schedule, maxCookingMinutes)
    val cookingStartTime = calculateCookingStart(request.requestedTime, now, schedule, maxCookingMinutes)
    if (request.requestedTime < cookingStartTime + maxCookingMinutes * 60_000L) {
        throw ApiException(HttpStatusCode.BadRequest, "INVALID_REQUESTED_TIME", "Requested time is too early for cooking time")
    }
    val totalPrice = builtItems.sumOf { it.totalPrice }
    val createdDate = Instant.ofEpochMilli(now).atZone(MoscowZone).toLocalDate()
    val datePart = createdDate.format(java.time.format.DateTimeFormatter.ofPattern("ddMM"))

    repeat(3) {
        val publicId = nextPublicId(connection, datePart, now)
        if (publicIdExists(connection, publicId)) return@repeat
        val savepoint = connection.setSavepoint("public_id_attempt")
        try {
            val orderId = insertOrder(
                connection,
                publicId,
                device.deviceId,
                request,
                cookingStartTime,
                totalPrice,
                now,
                paymentStatus = if (paymentRequired) "WAITING" else "PAID",
                paidAt = if (paymentRequired) null else now,
            )
            builtItems.forEach { insertOrderItem(connection, orderId, it) }
            insertStatusHistory(connection, orderId, null, "NEW", "system", now)
            connection.releaseSavepoint(savepoint)
            return CreatedOrder(id = orderId, publicId = publicId, totalPrice = totalPrice, updatedAt = now)
        } catch (error: SQLException) {
            connection.rollback(savepoint)
            if (error.sqlState != "23505") throw error
        }
    }
    throw ApiException(HttpStatusCode.Conflict, "PUBLIC_ID_CONFLICT", "Could not allocate public order id")
}

private data class OrderPaymentResult(
    val paymentStatus: String,
    val paymentUrl: String?,
    val updatedAt: Long,
)

private suspend fun initiateOrderPayment(
    config: AppConfig,
    tbankClient: TBankClient,
    database: AppDatabase,
    created: CreatedOrder,
): OrderPaymentResult {
    if (!tbankClient.config.enabled) {
        if (!config.paymentSkip) {
            throw ApiException(
                HttpStatusCode.ServiceUnavailable,
                "PAYMENT_NOT_CONFIGURED",
                "Онлайн-оплата не настроена. Укажите TBANK_TERMINAL_KEY и TBANK_PASSWORD в .env и перезапустите backend.",
            )
        }
        return OrderPaymentResult(paymentStatus = "PAID", paymentUrl = null, updatedAt = created.updatedAt)
    }
    val description = "Заказ ${created.publicId} МегаШаверма"
    val initResult = try {
        tbankClient.initPayment(
            orderId = created.publicId,
            amountRubles = created.totalPrice,
            description = description,
        )
    } catch (error: Throwable) {
        database.transaction { connection, now ->
            markOrderPaymentFailed(connection, created.id, null, now)
        }
        throw error
    }
    val updatedAt = database.transaction { connection, now ->
        connection.prepareStatement(
            """
            UPDATE orders
            SET tbank_payment_id = ?, payment_status = 'WAITING', updated_at = ?
            WHERE id = ? AND payment_status IN ('WAITING', 'FAILED')
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, initResult.paymentId)
            statement.setLong(2, now)
            statement.setLong(3, created.id)
            statement.executeUpdate()
        }
        now
    }
    return OrderPaymentResult(
        paymentStatus = "WAITING",
        paymentUrl = initResult.paymentUrl,
        updatedAt = updatedAt,
    )
}

private suspend fun handleTBankNotification(
    tbankClient: TBankClient,
    database: AppDatabase,
    notification: TBankNotification,
) {
    if (notification.orderId.isBlank()) return
    paymentLogger.info(
        "T-Bank webhook orderId={} status={} success={} paymentId={} errorCode={}",
        notification.orderId,
        notification.status,
        notification.success,
        notification.paymentId,
        notification.errorCode,
    )

    val order = database.read { connection ->
        findOrderByPublicId(connection, notification.orderId)
    } ?: run {
        paymentLogger.warn("T-Bank webhook ignored: order {} not found", notification.orderId)
        return
    }

    when {
        shouldConfirmPayment(notification) -> {
            val paymentId = notification.paymentId ?: order.tbankPaymentId
            if (paymentId == null) {
                paymentLogger.warn(
                    "T-Bank webhook ignored: no paymentId for order {}",
                    notification.orderId,
                )
                return
            }
            val expectedAmountKopecks = order.totalPrice.toLong() * 100
            if (notification.amount != null && notification.amount != expectedAmountKopecks) {
                paymentLogger.warn(
                    "T-Bank webhook ignored: amount mismatch for order {} (expected={}, got={})",
                    notification.orderId,
                    expectedAmountKopecks,
                    notification.amount,
                )
                return
            }

            val paymentState = try {
                tbankClient.getPaymentState(paymentId)
            } catch (error: Throwable) {
                paymentLogger.error(
                    "T-Bank GetState failed for order {} paymentId={}: {}",
                    notification.orderId,
                    paymentId,
                    error.message,
                )
                return
            }

            if (!tbankClient.isSuccessfulPayment(paymentState, order.publicId, expectedAmountKopecks)) {
                paymentLogger.warn(
                    "T-Bank payment not confirmed for order {}: status={} success={} errorCode={} amount={}",
                    notification.orderId,
                    paymentState.status,
                    paymentState.success,
                    paymentState.errorCode,
                    paymentState.amount,
                )
                return
            }

            database.transaction { connection, now ->
                markOrderPaid(connection, order.id, paymentId, now)
            }
            paymentLogger.info("Order {} marked PAID after GetState verification", notification.orderId)
        }
        shouldFailPayment(notification) -> {
            database.transaction { connection, now ->
                markOrderPaymentFailed(connection, order.id, notification.paymentId, now)
            }
            paymentLogger.info("Order {} marked FAILED from webhook status {}", notification.orderId, notification.status)
        }
    }
}

private fun shouldConfirmPayment(notification: TBankNotification): Boolean =
    notification.status.uppercase() == "CONFIRMED" &&
        notification.success &&
        TBankClient.isSuccessfulErrorCode(notification.errorCode)

private fun shouldFailPayment(notification: TBankNotification): Boolean =
    TBankClient.isFailedPaymentStatus(notification.status)


private fun markOrderPaid(connection: Connection, orderId: Long, paymentId: Long?, now: Long) {
    connection.prepareStatement(
        """
        UPDATE orders
        SET payment_status = 'PAID', paid_at = ?, tbank_payment_id = COALESCE(?, tbank_payment_id), updated_at = ?
        WHERE id = ? AND payment_status <> 'PAID'
        """.trimIndent()
    ).use { statement ->
        statement.setLong(1, now)
        if (paymentId == null) statement.setNull(2, Types.BIGINT) else statement.setLong(2, paymentId)
        statement.setLong(3, now)
        statement.setLong(4, orderId)
        statement.executeUpdate()
    }
}

private fun markOrderPaymentFailed(connection: Connection, orderId: Long, paymentId: Long?, now: Long) {
    connection.prepareStatement(
        """
        UPDATE orders
        SET payment_status = 'FAILED', tbank_payment_id = COALESCE(?, tbank_payment_id), updated_at = ?
        WHERE id = ? AND payment_status = 'WAITING'
        """.trimIndent()
    ).use { statement ->
        if (paymentId == null) statement.setNull(1, Types.BIGINT) else statement.setLong(1, paymentId)
        statement.setLong(2, now)
        statement.setLong(3, orderId)
        statement.executeUpdate()
    }
}

private fun markOrderCancelled(connection: Connection, orderId: Long, now: Long) {
    connection.prepareStatement(
        """
        UPDATE orders
        SET payment_status = 'CANCELLED', updated_at = ?
        WHERE id = ? AND payment_status IN ('WAITING', 'FAILED')
        """.trimIndent()
    ).use { statement ->
        statement.setLong(1, now)
        statement.setLong(2, orderId)
        statement.executeUpdate()
    }
}

private fun findUnpaidOrderByDevice(connection: Connection, deviceId: UUID): OrderRow? =
    connection.prepareStatement(
        """
        SELECT * FROM orders
        WHERE device_id = ? AND payment_status IN ('WAITING', 'FAILED')
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, deviceId)
        statement.executeQuery().use { result -> if (result.next()) result.toOrderRow() else null }
    }

private fun findOrderByPublicId(connection: Connection, publicId: String): OrderRow? =
    connection.prepareStatement("SELECT * FROM orders WHERE public_id = ?").use { statement ->
        statement.setString(1, publicId)
        statement.executeQuery().use { result -> if (result.next()) result.toOrderRow() else null }
    }

private fun buildOrderItem(connection: Connection, request: CreateOrderItemRequest): BuiltOrderItem {
    val item = findActiveMenuItem(connection, request.menuItemId)
        ?: throw ApiException(HttpStatusCode.BadRequest, "MENU_ITEM_NOT_FOUND", "Menu item not found or inactive")
    val additions = request.additionsIds.map { additionId ->
        findActiveAddition(connection, item.id, additionId)
            ?: throw ApiException(HttpStatusCode.BadRequest, "ADDITION_NOT_FOUND", "Addition not found or inactive")
    }
    val removals = request.removalsIds.map { removalId ->
        findActiveRemoval(connection, item.id, removalId)
            ?: throw ApiException(HttpStatusCode.BadRequest, "REMOVAL_NOT_FOUND", "Removal not found or inactive")
    }
    return BuiltOrderItem(
        menuItemId = item.id,
        name = item.name,
        price = item.price,
        weight = item.weight + additions.sumOf { it.weight },
        cookingTime = item.cookingTime,
        isGrill = item.isGrill,
        additions = additions,
        removals = removals,
    )
}

private fun validateRequestedTime(
    requestedTime: Long,
    now: Long,
    schedule: Map<Int, DaySchedule>,
    maxCookingMinutes: Int,
) {
    val requested = Instant.ofEpochMilli(requestedTime).atZone(MoscowZone)
    val nowZoned = Instant.ofEpochMilli(now).atZone(MoscowZone)
    val today = nowZoned.toLocalDate()
    val requestedDate = requested.toLocalDate()
    if (requestedDate.isBefore(today) || requestedDate.isAfter(today.plusDays(3))) {
        throw ApiException(HttpStatusCode.BadRequest, "INVALID_REQUESTED_TIME", "Requested date must be from today to today+3")
    }
    val daySchedule = schedule.forDate(requestedDate)
    if (requestedDate == today) {
        val nowTime = nowZoned.toLocalTime()
        if (nowTime.isAfter(daySchedule.lastOrderTime)) {
            throw ApiException(HttpStatusCode.BadRequest, "ORDER_ACCEPTANCE_CLOSED", "Today's order acceptance is closed")
        }
    }
    val requestedLocalTime = requested.toLocalTime()
    val minTime = daySchedule.openTime.plusMinutes(maxCookingMinutes.toLong())
    val maxTime = daySchedule.lastOrderTime.plusMinutes(maxCookingMinutes.toLong())
    val effectiveMin = if (requestedDate == today) {
        val nowPlusPrep = nowZoned.toLocalTime().plusMinutes(maxCookingMinutes.toLong())
        if (nowPlusPrep.isAfter(minTime)) nowPlusPrep else minTime
    } else {
        minTime
    }
    if (requestedLocalTime.isBefore(effectiveMin) || requestedLocalTime.isAfter(maxTime)) {
        throw ApiException(
            HttpStatusCode.BadRequest,
            "INVALID_REQUESTED_TIME",
            "Requested time must be between ${effectiveMin} and ${maxTime}",
        )
    }
}

private fun calculateCookingStart(
    requestedTime: Long,
    now: Long,
    schedule: Map<Int, DaySchedule>,
    maxCookingMinutes: Int,
): Long {
    val requested = Instant.ofEpochMilli(requestedTime).atZone(MoscowZone)
    val requestedDate = requested.toLocalDate()
    val daySchedule = schedule.forDate(requestedDate)
    val openInstant = requestedDate.atTime(daySchedule.openTime).atZone(MoscowZone).toInstant().toEpochMilli()
    var cookingStart = requestedTime - maxCookingMinutes * 60_000L
    cookingStart = max(cookingStart, now)
    cookingStart = max(cookingStart, openInstant)
    return cookingStart
}

private fun nextPublicId(connection: Connection, datePart: String, now: Long): String {
    connection.prepareStatement(
        """
        INSERT INTO daily_counter (date, counter, created_at, updated_at)
        VALUES (?, 0, ?, ?)
        ON CONFLICT (date) DO NOTHING
        """.trimIndent()
    ).use { statement ->
        statement.setString(1, datePart)
        statement.setLong(2, now)
        statement.setLong(3, now)
        statement.executeUpdate()
    }
    connection.prepareStatement("SELECT counter FROM daily_counter WHERE date = ? FOR UPDATE").use { statement ->
        statement.setString(1, datePart)
        statement.executeQuery().use { result -> result.next() }
    }
    val next = connection.prepareStatement(
        "UPDATE daily_counter SET counter = counter + 1, updated_at = ? WHERE date = ? RETURNING counter"
    ).use { statement ->
        statement.setLong(1, now)
        statement.setString(2, datePart)
        statement.executeQuery().use { result ->
            result.next()
            result.getInt(1)
        }
    }
    return "%s-%03d".format(datePart, next)
}

private fun publicIdExists(connection: Connection, publicId: String): Boolean =
    connection.prepareStatement("SELECT 1 FROM orders WHERE public_id = ?").use { statement ->
        statement.setString(1, publicId)
        statement.executeQuery().use { it.next() }
    }

private fun insertOrder(
    connection: Connection,
    publicId: String,
    deviceId: UUID,
    request: CreateOrderRequest,
    cookingStartTime: Long,
    totalPrice: Int,
    now: Long,
    paymentStatus: String,
    paidAt: Long?,
): Long {
    return connection.prepareStatement(
        """
        INSERT INTO orders (
            public_id, device_id, status, payment_status, paid_at, created_at, updated_at, requested_time,
            cooking_start_time, total_price, general_comment
        )
        VALUES (?, ?, 'NEW', ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id
        """.trimIndent()
    ).use { statement ->
        statement.setString(1, publicId)
        statement.setObject(2, deviceId)
        statement.setString(3, paymentStatus)
        if (paidAt == null) statement.setNull(4, Types.BIGINT) else statement.setLong(4, paidAt)
        statement.setLong(5, now)
        statement.setLong(6, now)
        statement.setLong(7, request.requestedTime)
        statement.setLong(8, cookingStartTime)
        statement.setInt(9, totalPrice)
        statement.setNullableString(10, request.generalComment?.trim()?.takeIf { it.isNotEmpty() })
        statement.executeQuery().use { result ->
            result.next()
            result.getLong(1)
        }
    }
}

private fun insertOrderItem(connection: Connection, orderId: Long, item: BuiltOrderItem) {
    connection.prepareStatement(
        """
        INSERT INTO order_items (
            order_id, menu_item_id, name_snapshot, price_snapshot, weight_snapshot,
            additions_snapshot, removals_snapshot
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
    ).use { statement ->
        statement.setLong(1, orderId)
        statement.setLong(2, item.menuItemId)
        statement.setString(3, item.name)
        statement.setInt(4, item.price)
        statement.setInt(5, item.weight)
        statement.setObject(6, jsonb(appJson.encodeToString(item.additions)))
        statement.setObject(7, jsonb(appJson.encodeToString(item.removals)))
        statement.executeUpdate()
    }
}

private fun updateOrderStatus(connection: Connection, publicId: String, targetStatus: String, now: Long): OrderDto {
    val allowed = listOf("NEW", "CONFIRMED", "COOKING", "READY", "COMPLETED")
    val order = connection.prepareStatement("SELECT * FROM orders WHERE public_id = ? FOR UPDATE").use { statement ->
        statement.setString(1, publicId)
        statement.executeQuery().use { result -> if (result.next()) result.toOrderRow() else null }
    } ?: throw ApiException(HttpStatusCode.NotFound, "ORDER_NOT_FOUND", "Order not found")
    if (order.paymentStatus != "PAID") {
        throw ApiException(HttpStatusCode.BadRequest, "PAYMENT_REQUIRED", "Order must be paid before kitchen processing")
    }
    val currentIndex = allowed.indexOf(order.status)
    val targetIndex = allowed.indexOf(targetStatus)
    if (currentIndex == -1 || targetIndex != currentIndex + 1) {
        throw ApiException(HttpStatusCode.BadRequest, "INVALID_STATUS_TRANSITION", "Only next status transition is allowed")
    }
    connection.prepareStatement("UPDATE orders SET status = ?, updated_at = ? WHERE id = ?").use { statement ->
        statement.setString(1, targetStatus)
        statement.setLong(2, now)
        statement.setLong(3, order.id)
        statement.executeUpdate()
    }
    insertStatusHistory(connection, order.id, order.status, targetStatus, "kitchen", now)
    return readOrderById(connection, order.id) ?: throw ApiException(HttpStatusCode.NotFound, "ORDER_NOT_FOUND", "Order not found")
}

private fun insertStatusHistory(connection: Connection, orderId: Long, oldStatus: String?, newStatus: String, changedBy: String, now: Long) {
    connection.prepareStatement(
        "INSERT INTO order_status_history (order_id, old_status, new_status, changed_by, changed_at) VALUES (?, ?, ?, ?, ?)"
    ).use { statement ->
        statement.setLong(1, orderId)
        statement.setNullableString(2, oldStatus)
        statement.setString(3, newStatus)
        statement.setString(4, changedBy)
        statement.setLong(5, now)
        statement.executeUpdate()
    }
}

private fun readMenu(connection: Connection, onlyActive: Boolean): List<CategoryDto> {
    val activeSql = if (onlyActive) "WHERE is_active = true" else ""
    val categories = connection.prepareStatement(
        "SELECT * FROM categories $activeSql ORDER BY sort_order, id"
    ).use { statement ->
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        CategoryDto(
                            id = result.getLong("id"),
                            name = result.getString("name"),
                            sortOrder = result.getInt("sort_order"),
                            isActive = result.getBoolean("is_active"),
                            isGrill = result.getBoolean("is_grill"),
                            defaultCookingMinutes = result.getInt("default_cooking_minutes"),
                        )
                    )
                }
            }
        }
    }
    val additions = readAdditions(connection, onlyActive).groupBy { it.menuItemId }
    val removals = readRemovals(connection, onlyActive).groupBy { it.menuItemId }
    val itemsByCategory = readAdminMenuItems(connection, onlyActive).groupBy { it.categoryId }
    return categories.map { category ->
        category.copy(
            items = (itemsByCategory[category.id] ?: emptyList()).map {
                it.copy(additions = additions[it.id] ?: emptyList(), removals = removals[it.id] ?: emptyList())
            }
        )
    }
}

private fun readAdminMenuItems(connection: Connection, onlyActive: Boolean = false): List<MenuItemDto> {
    val where = if (onlyActive) "WHERE mi.is_active = true AND c.is_active = true" else ""
    return connection.prepareStatement(
        """
        SELECT mi.*
        FROM menu_items mi
        JOIN categories c ON c.id = mi.category_id
        $where
        ORDER BY mi.sort_order, mi.id
        """.trimIndent()
    ).use { statement ->
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(result.toMenuItemDto())
                }
            }
        }
    }
}

private fun readAdditions(connection: Connection, onlyActive: Boolean): List<AdditionDto> {
    val where = if (onlyActive) "WHERE a.is_active = true AND mi.is_active = true AND c.is_active = true" else ""
    return connection.prepareStatement(
        """
        SELECT a.*
        FROM additions a
        JOIN menu_items mi ON mi.id = a.menu_item_id
        JOIN categories c ON c.id = mi.category_id
        $where
        ORDER BY a.id
        """.trimIndent()
    ).use { statement ->
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        AdditionDto(
                            id = result.getLong("id"),
                            menuItemId = result.getLong("menu_item_id"),
                            name = result.getString("name"),
                            price = result.getInt("price"),
                            weight = result.getInt("weight"),
                            isActive = result.getBoolean("is_active"),
                        )
                    )
                }
            }
        }
    }
}

private fun readRemovals(connection: Connection, onlyActive: Boolean): List<RemovalDto> {
    val where = if (onlyActive) "WHERE r.is_active = true AND mi.is_active = true AND c.is_active = true" else ""
    return connection.prepareStatement(
        """
        SELECT r.*
        FROM removals r
        JOIN menu_items mi ON mi.id = r.menu_item_id
        JOIN categories c ON c.id = mi.category_id
        $where
        ORDER BY r.id
        """.trimIndent()
    ).use { statement ->
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        RemovalDto(
                            id = result.getLong("id"),
                            menuItemId = result.getLong("menu_item_id"),
                            name = result.getString("name"),
                            isActive = result.getBoolean("is_active"),
                        )
                    )
                }
            }
        }
    }
}

private fun findActiveMenuItem(connection: Connection, id: Long): MenuItemRow? =
    connection.prepareStatement(
        """
        SELECT mi.*, c.is_grill
        FROM menu_items mi
        JOIN categories c ON c.id = mi.category_id
        WHERE mi.id = ? AND mi.is_active = true AND c.is_active = true
        """.trimIndent()
    ).use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { result -> if (result.next()) result.toMenuItemRow() else null }
    }

private fun findActiveAddition(connection: Connection, menuItemId: Long, id: Long): AdditionSnapshot? =
    connection.prepareStatement(
        "SELECT id, name, price, weight FROM additions WHERE id = ? AND menu_item_id = ? AND is_active = true"
    ).use { statement ->
        statement.setLong(1, id)
        statement.setLong(2, menuItemId)
        statement.executeQuery().use { result ->
            if (result.next()) AdditionSnapshot(result.getLong("id"), result.getString("name"), result.getInt("price"), result.getInt("weight")) else null
        }
    }

private fun findActiveRemoval(connection: Connection, menuItemId: Long, id: Long): RemovalSnapshot? =
    connection.prepareStatement(
        "SELECT id, name FROM removals WHERE id = ? AND menu_item_id = ? AND is_active = true"
    ).use { statement ->
        statement.setLong(1, id)
        statement.setLong(2, menuItemId)
        statement.executeQuery().use { result ->
            if (result.next()) RemovalSnapshot(result.getLong("id"), result.getString("name")) else null
        }
    }

private fun readOrdersForDevice(connection: Connection, deviceId: UUID, sinceUpdatedAt: Long, sinceId: Long): List<OrderDto> {
    val rows = connection.prepareStatement(
        """
        SELECT * FROM orders
        WHERE device_id = ? AND (updated_at > ? OR (updated_at = ? AND id > ?))
        ORDER BY updated_at ASC, id ASC
        """.trimIndent()
    ).use { statement ->
        statement.setObject(1, deviceId)
        statement.setLong(2, sinceUpdatedAt)
        statement.setLong(3, sinceUpdatedAt)
        statement.setLong(4, sinceId)
        statement.executeQuery().use { result -> readOrderRows(result) }
    }
    return rows.map { it.toOrderDto(readOrderItems(connection, it.id)) }
}

private fun readKitchenOrders(connection: Connection, sinceUpdatedAt: Long, sinceId: Long): List<KitchenOrderDto> {
    val firstPoll = sinceUpdatedAt == 0L && sinceId == 0L
    val startTime = if (firstPoll) {
        val today = Instant.ofEpochMilli(serverNow(connection)).atZone(MoscowZone).toLocalDate()
        today.minusDays(2).atStartOfDay(MoscowZone).toInstant().toEpochMilli()
    } else {
        0L
    }
    val rows = connection.prepareStatement(
        """
        SELECT o.*, d.client_number, d.name, d.phone, d.is_blocked, d.created_at AS client_created_at, d.updated_at AS client_updated_at
        FROM orders o
        JOIN devices d ON d.device_id = o.device_id
        WHERE (o.updated_at > ? OR (o.updated_at = ? AND o.id > ?))
          AND (? = 0 OR o.requested_time >= ?)
          AND o.payment_status = 'PAID'
        ORDER BY o.updated_at ASC, o.id ASC
        """.trimIndent()
    ).use { statement ->
        statement.setLong(1, sinceUpdatedAt)
        statement.setLong(2, sinceUpdatedAt)
        statement.setLong(3, sinceId)
        statement.setLong(4, startTime)
        statement.setLong(5, startTime)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        result.toOrderRow(
                            ClientDto(
                                deviceId = result.getObject("device_id", UUID::class.java).toString(),
                                clientNumber = result.getInt("client_number"),
                                name = result.getString("name"),
                                phone = result.getString("phone"),
                                isBlocked = result.getBoolean("is_blocked"),
                                createdAt = result.getLong("client_created_at"),
                                updatedAt = result.getLong("client_updated_at"),
                            )
                        )
                    )
                }
            }
        }
    }
    return rows.map { row ->
        KitchenOrderDto(
            id = row.id,
            publicId = row.publicId,
            status = row.status,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            requestedTime = row.requestedTime,
            cookingStartTime = row.cookingStartTime,
            totalPrice = row.totalPrice,
            generalComment = row.generalComment,
            items = readOrderItems(connection, row.id),
            client = row.client!!,
        )
    }
}

private fun readOrderById(connection: Connection, id: Long): OrderDto? =
    connection.prepareStatement("SELECT * FROM orders WHERE id = ?").use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { result ->
            if (result.next()) result.toOrderRow().toOrderDto(readOrderItems(connection, id)) else null
        }
    }

private fun readOrderItems(connection: Connection, orderId: Long): List<OrderItemDto> =
    connection.prepareStatement("SELECT * FROM order_items WHERE order_id = ? ORDER BY id").use { statement ->
        statement.setLong(1, orderId)
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) {
                    add(
                        OrderItemDto(
                            id = result.getLong("id"),
                            menuItemId = result.getLong("menu_item_id"),
                            nameSnapshot = result.getString("name_snapshot"),
                            priceSnapshot = result.getInt("price_snapshot"),
                            weightSnapshot = result.getInt("weight_snapshot"),
                            additionsSnapshot = appJson.decodeFromString(result.getString("additions_snapshot")),
                            removalsSnapshot = appJson.decodeFromString(result.getString("removals_snapshot")),
                        )
                    )
                }
            }
        }
    }

private fun readClients(connection: Connection, search: String?): List<ClientDto> {
    val where = if (search == null) "" else """
        WHERE CAST(device_id AS TEXT) ILIKE ?
           OR CAST(client_number AS TEXT) ILIKE ?
           OR COALESCE(phone, '') ILIKE ?
           OR COALESCE(name, '') ILIKE ?
    """.trimIndent()
    return connection.prepareStatement("SELECT * FROM devices $where ORDER BY updated_at DESC, client_number ASC").use { statement ->
        if (search != null) {
            val pattern = "%$search%"
            statement.setString(1, pattern)
            statement.setString(2, pattern)
            statement.setString(3, pattern)
            statement.setString(4, pattern)
        }
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) add(result.toDeviceRow().toClientDto())
            }
        }
    }
}

private fun readStats(connection: Connection, from: LocalDate, to: LocalDate): StatsBucket {
    val fromMs = from.atStartOfDay(MoscowZone).toInstant().toEpochMilli()
    val toMs = to.plusDays(1).atStartOfDay(MoscowZone).toInstant().toEpochMilli()
    return connection.prepareStatement(
        "SELECT COALESCE(SUM(total_price), 0), COUNT(*) FROM orders WHERE status = 'COMPLETED' AND requested_time >= ? AND requested_time < ?"
    ).use { statement ->
        statement.setLong(1, fromMs)
        statement.setLong(2, toMs)
        statement.executeQuery().use { result ->
            result.next()
            StatsBucket(totalSum = result.getLong(1), orderCount = result.getLong(2))
        }
    }
}

private fun readWeeklySchedule(connection: Connection): Map<Int, DaySchedule> =
    connection.prepareStatement(
        "SELECT day_of_week, open_time, last_order_time FROM weekly_schedule ORDER BY day_of_week"
    ).use { statement ->
        statement.executeQuery().use { result ->
            buildMap {
                while (result.next()) {
                    put(
                        result.getInt("day_of_week"),
                        DaySchedule(
                            openTime = LocalTime.parse(result.getString("open_time").take(5)),
                            lastOrderTime = LocalTime.parse(result.getString("last_order_time").take(5)),
                        ),
                    )
                }
            }
        }
    }

private fun Map<Int, DaySchedule>.forDate(date: java.time.LocalDate): DaySchedule =
    this[date.dayOfWeek.value]
        ?: throw ApiException(HttpStatusCode.InternalServerError, "SCHEDULE_NOT_FOUND", "Weekly schedule is not configured")

private fun Map<Int, DaySchedule>.toDtoList(): List<DayScheduleDto> =
    entries.sortedBy { it.key }.map { (day, schedule) ->
        DayScheduleDto(
            dayOfWeek = day,
            openTime = schedule.openTime.toString(),
            lastOrderTime = schedule.lastOrderTime.toString(),
        )
    }

private fun parseWeeklySchedule(element: JsonElement?): List<DayScheduleDto> {
    val array = element as? JsonArray
        ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "weekly_schedule must be an array")
    return array.map { item ->
        val objectBody = item as? JsonObject
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "weekly_schedule entries must be objects")
        val dayOfWeek = objectBody["day_of_week"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "day_of_week must be an integer")
        if (dayOfWeek !in 1..7) {
            throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "day_of_week must be between 1 and 7")
        }
        val openTime = objectBody["open_time"]?.jsonPrimitive?.content
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "open_time is required")
        val lastOrderTime = objectBody["last_order_time"]?.jsonPrimitive?.content
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "last_order_time is required")
        LocalTime.parse(openTime)
        LocalTime.parse(lastOrderTime)
        DayScheduleDto(dayOfWeek = dayOfWeek, openTime = openTime, lastOrderTime = lastOrderTime)
    }
}

private fun updateWeeklySchedule(connection: Connection, schedule: List<DayScheduleDto>, now: Long) {
    if (schedule.size != 7 || schedule.map { it.dayOfWeek }.toSet().size != 7) {
        throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "weekly_schedule must contain 7 unique days")
    }
    connection.prepareStatement(
        """
        UPDATE weekly_schedule
        SET open_time = ?, last_order_time = ?, updated_at = ?
        WHERE day_of_week = ?
        """.trimIndent()
    ).use { statement ->
        schedule.forEach { day ->
            statement.setTime(1, java.sql.Time.valueOf(day.openTime))
            statement.setTime(2, java.sql.Time.valueOf(day.lastOrderTime))
            statement.setLong(3, now)
            statement.setInt(4, day.dayOfWeek)
            statement.executeUpdate()
        }
    }
}

private fun readSettings(connection: Connection): Settings {
    val values = connection.prepareStatement("SELECT key, value FROM settings").use { statement ->
        statement.executeQuery().use { result ->
            buildMap {
                while (result.next()) put(result.getString("key"), result.getString("value"))
            }
        }
    }
    return Settings(
        workStart = LocalTime.parse(values["work_start_time"] ?: "00:00"),
        cutoffRegular = LocalTime.parse(values["cutoff_regular"] ?: "23:00"),
        cutoffGrill = LocalTime.parse(values["cutoff_grill"] ?: "23:00"),
    )
}

private fun readSettingsDtos(connection: Connection): List<SettingDto> =
    connection.prepareStatement("SELECT * FROM settings ORDER BY key").use { statement ->
        statement.executeQuery().use { result ->
            buildList {
                while (result.next()) add(SettingDto(result.getString("key"), result.getString("value"), result.getLong("updated_at")))
            }
        }
    }

private fun updateSettings(connection: Connection, body: JsonObject, now: Long): List<SettingDto> {
    if (body.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "Settings body must not be empty")
    body.forEach { (key, value) ->
        if (key == "weekly_schedule") return@forEach
        val stringValue = parseNullableString(value, key)
            ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "Setting value must be a string")
        if (key in setOf("work_start_time", "cutoff_regular", "cutoff_grill")) LocalTime.parse(stringValue)
        connection.prepareStatement(
            """
            INSERT INTO settings (key, value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, stringValue)
            statement.setLong(3, now)
            statement.executeUpdate()
        }
    }
    return readSettingsDtos(connection)
}

private fun createCategory(connection: Connection, request: CategoryUpsert): CategoryDto =
    connection.prepareStatement(
        "INSERT INTO categories (name, sort_order, is_active, is_grill, default_cooking_minutes) VALUES (?, ?, ?, ?, ?) RETURNING *"
    ).use { statement ->
        statement.setString(1, request.name)
        statement.setInt(2, request.sortOrder)
        statement.setBoolean(3, request.isActive)
        statement.setBoolean(4, request.isGrill)
        statement.setInt(5, request.defaultCookingMinutes)
        statement.executeQuery().use { result ->
            result.next()
            result.toCategoryDto()
        }
    }

private fun updateCategory(connection: Connection, id: Long, request: CategoryUpsert): CategoryDto {
    val previous = connection.prepareStatement("SELECT default_cooking_minutes FROM categories WHERE id = ?").use { statement ->
        statement.setLong(1, id)
        statement.executeQuery().use { result ->
            if (!result.next()) throw notFound("CATEGORY_NOT_FOUND", "Category not found")
            result.getInt("default_cooking_minutes")
        }
    }
    val updated = connection.prepareStatement(
        "UPDATE categories SET name = ?, sort_order = ?, is_active = ?, is_grill = ?, default_cooking_minutes = ? WHERE id = ? RETURNING *"
    ).use { statement ->
        statement.setString(1, request.name)
        statement.setInt(2, request.sortOrder)
        statement.setBoolean(3, request.isActive)
        statement.setBoolean(4, request.isGrill)
        statement.setInt(5, request.defaultCookingMinutes)
        statement.setLong(6, id)
        statement.executeQuery().use { result -> if (result.next()) result.toCategoryDto() else throw notFound("CATEGORY_NOT_FOUND", "Category not found") }
    }
    if (previous != request.defaultCookingMinutes) {
        val now = System.currentTimeMillis()
        connection.prepareStatement(
            "UPDATE menu_items SET cooking_time = ?, updated_at = ? WHERE category_id = ?"
        ).use { statement ->
            statement.setInt(1, request.defaultCookingMinutes)
            statement.setLong(2, now)
            statement.setLong(3, id)
            statement.executeUpdate()
        }
    }
    return updated
}

private fun createMenuItem(connection: Connection, request: MenuItemUpsert, now: Long): MenuItemDto =
    connection.prepareStatement(
        """
        INSERT INTO menu_items (category_id, name, description, price, weight, cooking_time, image_url, sort_order, is_active, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING *
        """.trimIndent()
    ).use { statement ->
        bindMenuItem(statement, request, now, includeId = false)
        statement.executeQuery().use { result ->
            result.next()
            result.toMenuItemDto()
        }
    }

private fun updateMenuItem(connection: Connection, id: Long, request: MenuItemUpsert, now: Long): MenuItemDto =
    connection.prepareStatement(
        """
        UPDATE menu_items
        SET category_id = ?, name = ?, description = ?, price = ?, weight = ?, cooking_time = ?,
            image_url = ?, sort_order = ?, is_active = ?, updated_at = ?
        WHERE id = ?
        RETURNING *
        """.trimIndent()
    ).use { statement ->
        bindMenuItem(statement, request, now, includeId = true, id = id)
        statement.executeQuery().use { result -> if (result.next()) result.toMenuItemDto() else throw notFound("MENU_ITEM_NOT_FOUND", "Menu item not found") }
    }

private fun bindMenuItem(statement: PreparedStatement, request: MenuItemUpsert, now: Long, includeId: Boolean, id: Long = 0) {
    statement.setLong(1, request.categoryId)
    statement.setString(2, request.name)
    statement.setNullableString(3, request.description)
    statement.setInt(4, request.price)
    statement.setInt(5, request.weight)
    statement.setInt(6, request.cookingTime)
    statement.setNullableString(7, request.imageUrl)
    statement.setInt(8, request.sortOrder)
    statement.setBoolean(9, request.isActive)
    if (includeId) {
        statement.setLong(10, now)
        statement.setLong(11, id)
    } else {
        statement.setLong(10, now)
        statement.setLong(11, now)
    }
}

private fun createAddition(connection: Connection, request: AdditionUpsert): AdditionDto =
    connection.prepareStatement(
        "INSERT INTO additions (menu_item_id, name, price, weight, is_active) VALUES (?, ?, ?, ?, ?) RETURNING *"
    ).use { statement ->
        statement.setLong(1, request.menuItemId)
        statement.setString(2, request.name)
        statement.setInt(3, request.price)
        statement.setInt(4, request.weight)
        statement.setBoolean(5, request.isActive)
        statement.executeQuery().use { result ->
            result.next()
            AdditionDto(result.getLong("id"), result.getLong("menu_item_id"), result.getString("name"), result.getInt("price"), result.getInt("weight"), result.getBoolean("is_active"))
        }
    }

private fun updateAddition(connection: Connection, id: Long, request: AdditionUpsert): AdditionDto =
    connection.prepareStatement(
        "UPDATE additions SET menu_item_id = ?, name = ?, price = ?, weight = ?, is_active = ? WHERE id = ? RETURNING *"
    ).use { statement ->
        statement.setLong(1, request.menuItemId)
        statement.setString(2, request.name)
        statement.setInt(3, request.price)
        statement.setInt(4, request.weight)
        statement.setBoolean(5, request.isActive)
        statement.setLong(6, id)
        statement.executeQuery().use { result ->
            if (!result.next()) throw notFound("ADDITION_NOT_FOUND", "Addition not found")
            AdditionDto(result.getLong("id"), result.getLong("menu_item_id"), result.getString("name"), result.getInt("price"), result.getInt("weight"), result.getBoolean("is_active"))
        }
    }

private fun createRemoval(connection: Connection, request: RemovalUpsert): RemovalDto =
    connection.prepareStatement(
        "INSERT INTO removals (menu_item_id, name, is_active) VALUES (?, ?, ?) RETURNING *"
    ).use { statement ->
        statement.setLong(1, request.menuItemId)
        statement.setString(2, request.name)
        statement.setBoolean(3, request.isActive)
        statement.executeQuery().use { result ->
            result.next()
            RemovalDto(result.getLong("id"), result.getLong("menu_item_id"), result.getString("name"), result.getBoolean("is_active"))
        }
    }

private fun updateRemoval(connection: Connection, id: Long, request: RemovalUpsert): RemovalDto =
    connection.prepareStatement(
        "UPDATE removals SET menu_item_id = ?, name = ?, is_active = ? WHERE id = ? RETURNING *"
    ).use { statement ->
        statement.setLong(1, request.menuItemId)
        statement.setString(2, request.name)
        statement.setBoolean(3, request.isActive)
        statement.setLong(4, id)
        statement.executeQuery().use { result ->
            if (!result.next()) throw notFound("REMOVAL_NOT_FOUND", "Removal not found")
            RemovalDto(result.getLong("id"), result.getLong("menu_item_id"), result.getString("name"), result.getBoolean("is_active"))
        }
    }

private fun softDelete(connection: Connection, table: String, id: Long): Map<String, Boolean> {
    val updated = connection.prepareStatement("UPDATE $table SET is_active = false WHERE id = ?").use { statement ->
        statement.setLong(1, id)
        statement.executeUpdate()
    }
    if (updated == 0) throw notFound("NOT_FOUND", "Entity not found")
    return mapOf("ok" to true)
}

private fun softDeleteMenuItem(connection: Connection, id: Long, now: Long): Map<String, Boolean> {
    val updated = connection.prepareStatement("UPDATE menu_items SET is_active = false, updated_at = ? WHERE id = ?").use { statement ->
        statement.setLong(1, now)
        statement.setLong(2, id)
        statement.executeUpdate()
    }
    if (updated == 0) throw notFound("MENU_ITEM_NOT_FOUND", "Menu item not found")
    return mapOf("ok" to true)
}

private fun findDevice(connection: Connection, deviceId: UUID): DeviceRow? =
    connection.prepareStatement("SELECT * FROM devices WHERE device_id = ?").use { statement ->
        statement.setObject(1, deviceId)
        statement.executeQuery().use { result -> if (result.next()) result.toDeviceRow() else null }
    }

private fun requireDevice(connection: Connection, deviceId: UUID): DeviceRow =
    findDevice(connection, deviceId) ?: throw deviceNotFound()

private fun findDeviceByClientNumber(connection: Connection, clientNumber: Int): DeviceRow? =
    connection.prepareStatement("SELECT * FROM devices WHERE client_number = ?").use { statement ->
        statement.setInt(1, clientNumber)
        statement.executeQuery().use { result -> if (result.next()) result.toDeviceRow() else null }
    }

private fun serverNow(connection: Connection): Long =
    connection.prepareStatement("SELECT (extract(epoch from now()) * 1000)::bigint").use { statement ->
        statement.executeQuery().use { result ->
            result.next()
            result.getLong(1)
        }
    }

private fun ResultSet.toDeviceRow(): DeviceRow = DeviceRow(
    deviceId = getObject("device_id", UUID::class.java),
    clientNumber = getInt("client_number"),
    name = getString("name"),
    phone = getString("phone"),
    isBlocked = getBoolean("is_blocked"),
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
)

private fun ResultSet.toMenuItemRow(): MenuItemRow = MenuItemRow(
    id = getLong("id"),
    categoryId = getLong("category_id"),
    name = getString("name"),
    description = getString("description"),
    price = getInt("price"),
    weight = getInt("weight"),
    cookingTime = getInt("cooking_time"),
    imageUrl = getString("image_url"),
    sortOrder = getInt("sort_order"),
    isActive = getBoolean("is_active"),
    isGrill = getBoolean("is_grill"),
)

private fun ResultSet.toMenuItemDto(): MenuItemDto = MenuItemDto(
    id = getLong("id"),
    categoryId = getLong("category_id"),
    name = getString("name"),
    description = getString("description"),
    price = getInt("price"),
    weight = getInt("weight"),
    cookingTime = getInt("cooking_time"),
    imageUrl = getString("image_url"),
    sortOrder = getInt("sort_order"),
    isActive = getBoolean("is_active"),
)

private fun ResultSet.toCategoryDto(): CategoryDto = CategoryDto(
    id = getLong("id"),
    name = getString("name"),
    sortOrder = getInt("sort_order"),
    isActive = getBoolean("is_active"),
    isGrill = getBoolean("is_grill"),
    defaultCookingMinutes = getInt("default_cooking_minutes"),
)

private fun ResultSet.toOrderRow(client: ClientDto? = null): OrderRow = OrderRow(
    id = getLong("id"),
    publicId = getString("public_id"),
    deviceId = getObject("device_id", UUID::class.java),
    status = getString("status"),
    paymentStatus = getString("payment_status"),
    tbankPaymentId = getObject("tbank_payment_id")?.let { (it as Number).toLong() },
    paidAt = getObject("paid_at")?.let { (it as Number).toLong() },
    createdAt = getLong("created_at"),
    updatedAt = getLong("updated_at"),
    requestedTime = getLong("requested_time"),
    cookingStartTime = getLong("cooking_start_time"),
    totalPrice = getInt("total_price"),
    generalComment = getString("general_comment"),
    client = client,
)

private fun readOrderRows(result: ResultSet): List<OrderRow> = buildList {
    while (result.next()) add(result.toOrderRow())
}

private fun DeviceRow.toInitResponse(serverTime: Long): InitResponse = InitResponse(
    clientNumber = clientNumber,
    name = name,
    phone = phone,
    isBlocked = isBlocked,
    serverTime = serverTime,
)

private fun DeviceRow.toClientDto(): ClientDto = ClientDto(
    deviceId = deviceId.toString(),
    clientNumber = clientNumber,
    name = name,
    phone = phone,
    isBlocked = isBlocked,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun OrderRow.toOrderDto(items: List<OrderItemDto>): OrderDto = OrderDto(
    id = id,
    publicId = publicId,
    status = status,
    paymentStatus = paymentStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    requestedTime = requestedTime,
    cookingStartTime = cookingStartTime,
    totalPrice = totalPrice,
    generalComment = generalComment,
    items = items,
)

private fun parseUuid(value: String): UUID =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw ApiException(HttpStatusCode.BadRequest, "INVALID_UUID", "Invalid UUID")
    }

private fun io.ktor.server.application.ApplicationCall.deviceIdHeader(): UUID {
    val raw = request.headers["X-Device-Id"] ?: throw deviceNotFound()
    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw deviceNotFound()
    }
}

private fun parseNullableString(element: JsonElement?, field: String): String? =
    when (element) {
        null, JsonNull -> null
        is JsonPrimitive -> element.contentOrNull
        else -> throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "$field must be a string or null")
    }

private fun io.ktor.server.application.ApplicationCall.pathId(): Long = parameters["id"]?.toLongOrNull()
    ?: throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "id must be an integer")

private fun CategoryUpsert.validate(): CategoryUpsert {
    if (name.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "name is required")
    if (defaultCookingMinutes < 0) throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "default_cooking_minutes must be >= 0")
    return copy(name = name.trim())
}

private fun MenuItemUpsert.validate(): MenuItemUpsert {
    if (name.isBlank() || price < 0 || weight < 0 || cookingTime < 0) {
        throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "Invalid menu item")
    }
    return copy(name = name.trim(), description = description?.trim(), imageUrl = imageUrl?.trim())
}

private fun AdditionUpsert.validate(): AdditionUpsert {
    if (name.isBlank() || price < 0 || weight < 0) throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "Invalid addition")
    return copy(name = name.trim())
}

private fun RemovalUpsert.validate(): RemovalUpsert {
    if (name.isBlank()) throw ApiException(HttpStatusCode.BadRequest, "BAD_REQUEST", "Invalid removal")
    return copy(name = name.trim())
}

private fun PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
}

private fun jsonb(value: String): PGobject = PGobject().apply {
    type = "jsonb"
    this.value = value
}

private fun deviceNotFound(): ApiException =
    ApiException(HttpStatusCode.NotFound, "DEVICE_NOT_FOUND", "Device not found")

private fun notFound(error: String, message: String): ApiException =
    ApiException(HttpStatusCode.NotFound, error, message)
