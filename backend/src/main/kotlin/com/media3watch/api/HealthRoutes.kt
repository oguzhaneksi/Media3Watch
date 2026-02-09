package com.media3watch.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "status" to "healthy",
                "timestamp" to System.currentTimeMillis(),
            ),
        )
    }
}
