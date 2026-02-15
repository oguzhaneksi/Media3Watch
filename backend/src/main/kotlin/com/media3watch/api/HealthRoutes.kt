package com.media3watch.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "status" to "healthy",
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}

