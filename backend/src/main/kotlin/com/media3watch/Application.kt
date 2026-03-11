package com.media3watch

import com.media3watch.api.healthRoutes
import com.media3watch.api.metricsRoutes
import com.media3watch.api.sessionsRoutes
import com.media3watch.config.ApiConstants
import com.media3watch.config.AppConfig
import com.media3watch.config.configurePlugins
import com.media3watch.db.DatabaseFactory
import com.media3watch.db.DefaultSessionRepository
import com.media3watch.db.RetentionScheduler
import com.media3watch.db.SessionRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import javax.sql.DataSource

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
        host = ApiConstants.SERVER_HOST
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

    // Prometheus registry + optional JVM metrics
    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    if (config.enableMetrics) {
        JvmMemoryMetrics().bindTo(prometheusRegistry)
        JvmGcMetrics().bindTo(prometheusRegistry)
        JvmThreadMetrics().bindTo(prometheusRegistry)
        ClassLoaderMetrics().bindTo(prometheusRegistry)
    }

    // Business metric counters
    val sessionsIngestedCounter = Counter.builder(ApiConstants.Observability.SESSIONS_INGESTED_COUNTER)
        .description("Total number of successfully ingested sessions")
        .register(prometheusRegistry)
    val sessionsFailedCounter = Counter.builder(ApiConstants.Observability.SESSIONS_FAILED_COUNTER)
        .description("Total number of failed session ingestions")
        .register(prometheusRegistry)

    // Plugin configuration (ContentNegotiation, CallLogging, StatusPages, RateLimit, Authentication)
    configurePlugins(config)

    // Routing
    routing {
        if (dataSource != null) {
            healthRoutes(dataSource)
        }
        if (config.enableMetrics) {
            authenticate(ApiConstants.AUTH_CONFIG_NAME) {
                metricsRoutes(prometheusRegistry)
            }
        }
        rateLimit(RateLimitName(ApiConstants.RATE_LIMIT_NAME)) {
            install(RequestBodyLimit) {
                bodyLimit { 256 * 1024L }
            }
            sessionsRoutes(repository, sessionsIngestedCounter, sessionsFailedCounter)
        }
    }

    // Scheduled data retention cleanup — only runs in production (when a real DataSource is present)
    val retentionJob = if (dataSource != null) {
        RetentionScheduler(repository, config.retentionDays).start(this)
    } else null

    // Graceful shutdown: cancel the retention job when the application stops
    monitor.subscribe(ApplicationStopped) {
        logger.info("Application stopping — cancelling retention cleanup job")
        retentionJob?.cancel()
    }

    logger.info("Application started successfully")
}
