package com.media3watch.api

import com.media3watch.config.ApiConstants
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
    get(ApiConstants.Routes.HEALTH) {
        val dbStatus = withContext(Dispatchers.IO) {
            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(ApiConstants.Database.HEALTH_CHECK_SQL).use { stmt ->
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
                HealthResponse(status = ApiConstants.HealthStatus.HEALTHY, database = ApiConstants.HealthStatus.CONNECTED)
            )
        } else {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                HealthResponse(status = ApiConstants.HealthStatus.UNHEALTHY, database = ApiConstants.HealthStatus.DISCONNECTED)
            )
        }
    }
}
