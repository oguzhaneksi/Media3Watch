package com.media3watch.db

import com.media3watch.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun createDataSource(config: AppConfig): DataSource {
        logger.info("Initializing HikariCP connection pool")
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.databaseUrl
            username = config.databaseUser
            password = config.databasePassword
            driverClassName = "org.postgresql.Driver"

            // Pool settings
            maximumPoolSize = config.hikariMaxPoolSize
            minimumIdle = config.hikariMinIdle
            connectionTimeout = 30000      // 30 seconds
            idleTimeout = 600000           // 10 minutes
            maxLifetime = 1800000          // 30 minutes
            leakDetectionThreshold = 60000 // 1 minute

            // Performance tuning
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    fun runMigrations(dataSource: DataSource) {
        logger.info("Running Flyway database migrations")
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()

        val result = flyway.migrate()
        logger.info("Applied ${result.migrationsExecuted} migrations successfully")
    }
}

