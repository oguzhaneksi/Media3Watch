package com.media3watch

import com.media3watch.api.healthRoutes
import com.media3watch.api.metricsRoutes
import com.media3watch.api.sessionsRoutes
import com.media3watch.config.AppConfig
import com.media3watch.db.DatabaseFactory
import com.media3watch.db.SessionRepository
import com.media3watch.observability.ErrorCodes
import com.media3watch.observability.ErrorDetail
import com.media3watch.observability.ErrorResponse
import com.media3watch.security.apiKey
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.rateLimit
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = AppConfig.fromEnvironment()
    val logger = LoggerFactory.getLogger("Application")

    logger.info("Starting Media3Watch Backend")
    logger.info("API Key configured: ${config.apiKey.take(4)}***")
    logger.info("Database URL: ${config.databaseUrl}")

    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
    ) {
        module(config)
    }.start(wait = true)
}

private fun initializeDatabase(config: AppConfig): SessionRepository {
    val dataSource = DatabaseFactory.createDataSource(config)
    DatabaseFactory.runMigrations(dataSource)
    return SessionRepository(dataSource)
}

data class MetricsConfig(
    val prometheusRegistry: PrometheusMeterRegistry,
    val sessionsIngestedCounter: Counter,
    val sessionsFailedCounter: Counter,
)

private fun setupMetrics(config: AppConfig): MetricsConfig {
    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    if (config.enableMetrics) {
        JvmMemoryMetrics().bindTo(prometheusRegistry)
        JvmGcMetrics().bindTo(prometheusRegistry)
        JvmThreadMetrics().bindTo(prometheusRegistry)
        ClassLoaderMetrics().bindTo(prometheusRegistry)
    }

    val sessionsIngestedCounter =
        Counter
            .builder("sessions_ingested_total")
            .description("Total number of successfully ingested sessions")
            .register(prometheusRegistry)

    val sessionsFailedCounter =
        Counter
            .builder("sessions_failed_total")
            .description("Total number of failed session ingestions")
            .register(prometheusRegistry)

    return MetricsConfig(prometheusRegistry, sessionsIngestedCounter, sessionsFailedCounter)
}

private fun Application.installPlugins(config: AppConfig) {
    val logger = LoggerFactory.getLogger("Application")

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            },
        )
    }

    install(CallLogging) {
        level =
            when (config.logLevel.uppercase()) {
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
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    ErrorDetail(
                        code = ErrorCodes.INTERNAL_ERROR,
                        message = "Unexpected server error",
                    ),
                ),
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
}

fun Application.module(config: AppConfig = AppConfig.fromEnvironment()) {
    val logger = LoggerFactory.getLogger("Application")

    val sessionRepository = initializeDatabase(config)
    val metricsConfig = setupMetrics(config)
    installPlugins(config)

    routing {
        healthRoutes()

        if (config.enableMetrics) {
            metricsRoutes(metricsConfig.prometheusRegistry)
        }

        rateLimit(RateLimitName("api-key-limit")) {
            sessionsRoutes(
                sessionRepository,
                metricsConfig.sessionsIngestedCounter,
                metricsConfig.sessionsFailedCounter,
            )
        }
    }

    logger.info("Application started successfully")
}
