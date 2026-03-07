package com.media3watch

import com.media3watch.api.healthRoutes
import com.media3watch.api.metricsRoutes
import com.media3watch.api.sessionsRoutes
import com.media3watch.config.AppConfig
import com.media3watch.db.DatabaseFactory
import com.media3watch.db.DefaultSessionRepository
import com.media3watch.db.SessionRepository
import com.media3watch.observability.ErrorCodes
import com.media3watch.observability.ErrorDetail
import com.media3watch.observability.ErrorResponse
import com.media3watch.security.apiKey
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import javax.sql.DataSource
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = AppConfig.fromEnvironment()
    val logger = LoggerFactory.getLogger("Application")

    logger.info("Starting Media3Watch Backend")
    logger.info("API Key configured: ${config.apiKey.take(4)}***")
    logger.info("Database URL: ${config.databaseUrl}")

    val dataSource = DatabaseFactory.createDataSource(config)
    DatabaseFactory.runMigrations(dataSource)

    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0"
    ) {
        module(
            config = config,
            repository = DefaultSessionRepository(dataSource),
            dataSource = dataSource
        )
    }.start(wait = true)
}

fun Application.module(
    config: AppConfig = AppConfig.fromEnvironment(),
    repository: SessionRepository,
    dataSource: DataSource? = null  // null in tests → health route omitted
) {
    val logger = LoggerFactory.getLogger("Application")

    // Prometheus metrics
    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    if (config.enableMetrics) {
        // JVM metrics
        JvmMemoryMetrics().bindTo(prometheusRegistry)
        JvmGcMetrics().bindTo(prometheusRegistry)
        JvmThreadMetrics().bindTo(prometheusRegistry)
        ClassLoaderMetrics().bindTo(prometheusRegistry)
    }

    // Custom business metrics
    val sessionsIngestedCounter = Counter.builder("sessions_ingested_total")
        .description("Total number of successfully ingested sessions")
        .register(prometheusRegistry)

    val sessionsFailedCounter = Counter.builder("sessions_failed_total")
        .description("Total number of failed session ingestions")
        .register(prometheusRegistry)

    // Install plugins
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CallLogging) {
        level = when (config.logLevel.uppercase()) {
            "DEBUG" -> Level.DEBUG
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            else -> Level.INFO
        }
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            "$method $path - $status (${duration}ms)"
        }
        filter { call ->
            !call.request.path().startsWith("/health")
        }
    }

    install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Payload exceeds maximum size"))
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    ErrorDetail(
                        code = ErrorCodes.INTERNAL_ERROR,
                        message = "Unexpected server error"
                    )
                )
            )
        }
    }

    install(RateLimit) {
        register(RateLimitName("api-key-limit")) {
            rateLimiter(limit = config.rateLimitRequests, refillPeriod = config.rateLimitWindowSec.seconds)
            requestKey { applicationCall ->
                applicationCall.request.headers["X-API-Key"] ?: "anonymous"
            }
        }
    }

    install(Authentication) {
        apiKey("api-key-auth") {
            keyProvider = { config.apiKey }
        }
    }

    // Configure routing
    routing {
        if (dataSource != null) {
            healthRoutes(dataSource)
        }

        if (config.enableMetrics) {
            authenticate("api-key-auth") {
                metricsRoutes(prometheusRegistry)
            }
        }

        rateLimit(RateLimitName("api-key-limit")) {
            install(RequestBodyLimit) {
                bodyLimit { 256 * 1024L }
            }
            sessionsRoutes(repository, sessionsIngestedCounter, sessionsFailedCounter)
        }
    }

    // Scheduled data retention cleanup — only runs in production (when a real DataSource is present)
    val retentionJob = if (dataSource != null) launch {
        while (isActive) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val deleted = repository.deleteExpiredSessions(config.retentionDays)
                    logger.info("Retention cleanup: deleted $deleted expired sessions (retentionDays=${config.retentionDays})")
                }.onFailure { e ->
                    logger.error("Retention cleanup failed", e)
                }
            }
            delay(24.hours)
        }
    } else null

    // Graceful shutdown: cancel the retention job when the application stops.
    monitor.subscribe(ApplicationStopped) {
        logger.info("Application stopping — cancelling retention cleanup job")
        retentionJob?.cancel()
    }

    logger.info("Application started successfully")
}
