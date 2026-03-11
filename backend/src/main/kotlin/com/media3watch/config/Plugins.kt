package com.media3watch.config

import com.media3watch.observability.ErrorCodes
import com.media3watch.observability.ErrorDetail
import com.media3watch.observability.ErrorResponse
import com.media3watch.security.apiKey
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("Plugins")

/**
 * Installs and configures all Ktor plugins.
 *
 * Extracted from [com.media3watch.main] so that the module function is focused on
 * orchestration only. Each plugin block has a single, well-defined purpose.
 */
internal fun Application.configurePlugins(
    config: AppConfig
) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CallLogging) {
        level = when (config.logLevel.uppercase()) {
            "DEBUG" -> Level.DEBUG
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            else -> Level.INFO
        }
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            "$method $path - $status (${duration}ms)"
        }
        filter { call ->
            !call.request.path().startsWith(ApiConstants.Routes.HEALTH)
        }
    }

    install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse(ErrorDetail(code = ErrorCodes.INVALID_SCHEMA, message = "Payload exceeds maximum size"))
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    ErrorDetail(
                        code = ErrorCodes.INTERNAL_ERROR,
                        message = "Unexpected server error"
                    )
                )
            )
        }
    }

    install(RateLimit) {
        register(RateLimitName(ApiConstants.RATE_LIMIT_NAME)) {
            rateLimiter(limit = config.rateLimitRequests, refillPeriod = config.rateLimitWindowSec.seconds)
            requestKey { call ->
                call.request.headers[ApiConstants.API_KEY_HEADER] ?: ApiConstants.ANONYMOUS_RATE_LIMIT_KEY
            }
        }
    }

    install(Authentication) {
        apiKey(ApiConstants.AUTH_CONFIG_NAME) {
            keyProvider = { config.apiKey }
        }
    }
}
