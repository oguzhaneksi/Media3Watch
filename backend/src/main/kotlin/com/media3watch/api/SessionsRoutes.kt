package com.media3watch.api

import com.media3watch.config.ApiConstants
import com.media3watch.db.SessionRepository
import com.media3watch.domain.SessionSummary
import com.media3watch.observability.ErrorCodes
import com.media3watch.observability.ErrorDetail
import com.media3watch.observability.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Counter
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory


private val logger = LoggerFactory.getLogger("SessionsRoutes")

@Serializable
data class SessionResponse(
    val status: String,
    val sessionId: String
)

fun Route.sessionsRoutes(
    repository: SessionRepository,
    sessionsIngestedCounter: Counter,
    sessionsFailedCounter: Counter
) {
    authenticate(ApiConstants.AUTH_CONFIG_NAME) {
        post(ApiConstants.Routes.SESSIONS) {
            try {
                val session = call.receive<SessionSummary>()

                // Validate top-level session fields
                val sessionError = SessionValidator.validate(session)
                if (sessionError != null) {
                    call.respondValidationError(sessionError)
                    return@post
                }

                // Validate timeline entries if present
                val timelineEvents = session.timelineEvents
                if (timelineEvents != null) {
                    val timelineError = SessionValidator.validateTimeline(timelineEvents)
                    if (timelineError != null) {
                        call.respondValidationError(timelineError)
                        return@post
                    }
                }

                val result = repository.upsertSessionWithTimeline(session, timelineEvents)

                result.onSuccess {
                    sessionsIngestedCounter.increment()
                    call.respond(HttpStatusCode.OK, SessionResponse("success", session.sessionId))
                }.onFailure { error ->
                    sessionsFailedCounter.increment()
                    logger.error("Failed to upsert session with timeline", error)
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse(
                            ErrorDetail(
                                code = ErrorCodes.DATABASE_ERROR,
                                message = "Temporary storage issue"
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Error processing session request", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        ErrorDetail(
                            code = ErrorCodes.INVALID_SCHEMA,
                            message = "Invalid request body"
                        )
                    )
                )
            }
        }
    }
}
