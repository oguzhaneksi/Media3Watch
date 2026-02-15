package com.media3watch.security

import com.media3watch.observability.ErrorCodes
import com.media3watch.observability.ErrorDetail
import com.media3watch.observability.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

data class ApiKeyPrincipal(val key: String)

class ApiKeyAuthenticationProvider internal constructor(
    config: Config
) : AuthenticationProvider(config) {
    private val keyProvider: () -> String = config.keyProvider

    class Config internal constructor(name: String) : AuthenticationProvider.Config(name) {
        lateinit var keyProvider: () -> String

        fun build() = ApiKeyAuthenticationProvider(this)
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val apiKey = context.call.request.headers["X-API-Key"]
        val expectedKey = keyProvider()

        if (apiKey != null && apiKey == expectedKey) {
            context.principal(ApiKeyPrincipal(apiKey))
        } else {
            context.challenge("ApiKeyAuth", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        ErrorDetail(
                            code = ErrorCodes.INVALID_API_KEY,
                            message = "Invalid or missing API Key"
                        )
                    )
                )
                challenge.complete()
            }
        }
    }
}

fun AuthenticationConfig.apiKey(
    name: String,
    configure: ApiKeyAuthenticationProvider.Config.() -> Unit
) {
    val provider = ApiKeyAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

