package com.media3watch.config

/**
 * Centralized string constants shared across the application.
 * Replaces magic strings scattered across route, auth, and infrastructure files.
 */
internal object ApiConstants {

    const val API_KEY_HEADER = "X-API-Key"
    const val AUTH_CONFIG_NAME = "api-key-auth"
    const val RATE_LIMIT_NAME = "api-key-limit"
    const val ANONYMOUS_RATE_LIMIT_KEY = "anonymous"
    const val AUTH_CHALLENGE_KEY = "ApiKeyAuth"
    const val SERVER_HOST = "0.0.0.0"

    object Routes {
        const val HEALTH = "/health"
        const val SESSIONS = "/v1/sessions"
        const val METRICS = "/metrics"
    }

    object HealthStatus {
        const val HEALTHY = "healthy"
        const val UNHEALTHY = "unhealthy"
        const val CONNECTED = "connected"
        const val DISCONNECTED = "disconnected"
    }

    object Database {
        const val DRIVER = "org.postgresql.Driver"
        const val MIGRATION_LOCATION = "classpath:db/migration"
        const val HEALTH_CHECK_SQL = "SELECT 1"
    }

    object Observability {
        const val PROMETHEUS_CONTENT_TYPE = "text/plain; version=0.0.4"
        const val SESSIONS_INGESTED_COUNTER = "sessions_ingested_total"
        const val SESSIONS_FAILED_COUNTER = "sessions_failed_total"
    }
}
