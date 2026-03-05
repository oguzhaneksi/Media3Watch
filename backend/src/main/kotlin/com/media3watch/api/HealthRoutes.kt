package com.media3watch.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("HealthRoutes")

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun Route.healthRoutes(dataSource: DataSource) {
    get("/health") {
        val dbStatus = withContext(Dispatchers.IO) {
            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement("SELECT 1").use { stmt ->
                        stmt.executeQuery().close()
                    }
                }
                true
            } catch (e: Exception) {
                logger.error("Health check: database connectivity failed", e)
                false
            }
        }

        if (dbStatus) {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(status = "healthy", database = "connected")
            )
        } else {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                HealthResponse(status = "unhealthy", database = "disconnected")
            )
        }
    }
}
