package com.media3watch.api

import com.media3watch.observability.ErrorCodes
import com.media3watch.observability.ErrorDetail
import com.media3watch.observability.ErrorResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Responds with a 400 Bad Request using the standard INVALID_SCHEMA error format.
 * Eliminates the repeated `call.respond(HttpStatusCode.BadRequest, ErrorResponse(ErrorDetail(...)))` boilerplate.
 */
internal suspend fun RoutingCall.respondValidationError(message: String) {
    respond(
        HttpStatusCode.BadRequest,
        ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = message))
    )
}
