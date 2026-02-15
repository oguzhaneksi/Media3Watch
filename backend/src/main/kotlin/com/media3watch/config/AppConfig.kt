package com.media3watch.config

data class AppConfig(
    val apiKey: String,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val rateLimitRequests: Int,
    val rateLimitWindowSec: Int,
    val retentionDays: Int,
    val hikariMaxPoolSize: Int,
    val hikariMinIdle: Int,
    val logLevel: String,
    val enableMetrics: Boolean
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            return AppConfig(
                apiKey = System.getenv("M3W_API_KEY") ?: "dev-key",
                databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/media3watch",
                databaseUser = System.getenv("DATABASE_USER") ?: "m3w",
                databasePassword = System.getenv("DATABASE_PASSWORD") ?: "m3w",
                rateLimitRequests = System.getenv("RATE_LIMIT_REQUESTS")?.toIntOrNull() ?: 100,
                rateLimitWindowSec = System.getenv("RATE_LIMIT_WINDOW_SEC")?.toIntOrNull() ?: 60,
                retentionDays = System.getenv("RETENTION_DAYS")?.toIntOrNull() ?: 90,
                hikariMaxPoolSize = System.getenv("HIKARI_MAX_POOL_SIZE")?.toIntOrNull() ?: 20,
                hikariMinIdle = System.getenv("HIKARI_MIN_IDLE")?.toIntOrNull() ?: 5,
                logLevel = System.getenv("LOG_LEVEL") ?: "INFO",
                enableMetrics = System.getenv("ENABLE_METRICS")?.toBoolean() ?: true
            )
        }
    }
}

