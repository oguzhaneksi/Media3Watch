package com.media3watch.api

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

private val UUID_PATTERN = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE
)
private const val MAX_SESSION_ID_LENGTH = 128
private const val MAX_PAYLOAD_BYTES = 64 * 1024L // 64 KB

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
    authenticate("api-key-auth") {
        post("/v1/sessions") {
            try {
                // Reject oversized payloads before deserialization
                val contentLength = call.request.contentLength()
                if (contentLength != null && contentLength > MAX_PAYLOAD_BYTES) {
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Payload exceeds maximum size"))
                    )
                    return@post
                }

                val session = call.receive<SessionSummary>()

                // Validate sessionId: non-blank, max length, UUID format
                if (session.sessionId.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Missing or empty required field: sessionId"))
                    )
                    return@post
                }

                if (session.sessionId.length > MAX_SESSION_ID_LENGTH) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "sessionId exceeds maximum length of $MAX_SESSION_ID_LENGTH characters"))
                    )
                    return@post
                }

                if (!UUID_PATTERN.matches(session.sessionId)) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "sessionId must be in UUID format"))
                    )
                    return@post
                }

                // Validate sessionDurationMs
                if (session.sessionDurationMs <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Invalid value for sessionDurationMs: must be positive"))
                    )
                    return@post
                }

                // Validate timestamp
                if (session.timestamp <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Invalid value for timestamp: must be positive"))
                    )
                    return@post
                }

                // Validate sessionStartDateIso
                if (session.sessionStartDateIso.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Missing or empty required field: sessionStartDateIso"))
                    )
                    return@post
                }

                // Validate nullable numeric fields: all must be non-negative if provided
                val outOfRangeFields = buildList {
                    if (session.startupTimeMs != null && session.startupTimeMs < 0) add("startupTimeMs")
                    if (session.rebufferTimeMs != null && session.rebufferTimeMs < 0) add("rebufferTimeMs")
                    if (session.rebufferCount != null && session.rebufferCount < 0) add("rebufferCount")
                    if (session.playTimeMs != null && session.playTimeMs < 0) add("playTimeMs")
                    if (session.rebufferRatio != null && (session.rebufferRatio < 0f || session.rebufferRatio > 1f)) add("rebufferRatio")
                    if (session.totalDroppedFrames != null && session.totalDroppedFrames < 0) add("totalDroppedFrames")
                    if (session.totalSeekCount != null && session.totalSeekCount < 0) add("totalSeekCount")
                    if (session.totalSeekTimeMs != null && session.totalSeekTimeMs < 0) add("totalSeekTimeMs")
                    if (session.errorCount != null && session.errorCount < 0) add("errorCount")
                }
                if (outOfRangeFields.isNotEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Out-of-range value for field(s): ${outOfRangeFields.joinToString()}: must be non-negative"))
                    )
                    return@post
                }

                val result = repository.upsertSession(session)

                result.onSuccess {
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

