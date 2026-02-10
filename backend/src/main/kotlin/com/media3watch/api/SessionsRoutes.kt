package com.media3watch.api

import com.media3watch.db.SessionRepository
import com.media3watch.domain.SessionSummary
import com.media3watch.observability.ErrorCodes
import com.media3watch.observability.ErrorDetail
import com.media3watch.observability.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.micrometer.core.instrument.Counter
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SessionsRoutes")

@Serializable
data class SessionResponse(
    val status: String,
    val sessionId: String,
)

fun Route.sessionsRoutes(
    repository: SessionRepository,
    sessionsIngestedCounter: Counter,
    sessionsFailedCounter: Counter,
) {
    authenticate("api-key-auth") {
        post("/v1/sessions") {
            try {
                val session = call.receive<SessionSummary>()

                // Validate required fields
                if (session.sessionId.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            ErrorDetail(
                                code = ErrorCodes.INVALID_SCHEMA,
                                message = "Missing required field: sessionId",
                            ),
                        ),
                    )
                    return@post
                }

                val result = repository.upsertSession(session)

                result
                    .onSuccess {
                        sessionsIngestedCounter.increment()
                        call.respond(HttpStatusCode.OK, SessionResponse("success", session.sessionId))
                    }.onFailure { error ->
                        sessionsFailedCounter.increment()
                        logger.error("Failed to upsert session", error)
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            ErrorResponse(
                                ErrorDetail(
                                    code = ErrorCodes.DATABASE_ERROR,
                                    message = "Temporary storage issue",
                                ),
                            ),
                        )
                    }
            } catch (e: SerializationException) {
                logger.error("Invalid session payload", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        ErrorDetail(
                            code = ErrorCodes.INVALID_SCHEMA,
                            message = "Invalid request body: ${e.message}",
                        ),
                    ),
                )
            }
        }
    }
}
