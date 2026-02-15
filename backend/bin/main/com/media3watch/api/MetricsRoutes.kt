package com.media3watch.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Route.metricsRoutes(registry: PrometheusMeterRegistry) {
    get("/metrics") {
        call.respondText(
            registry.scrape(),
            ContentType.parse("text/plain; version=0.0.4")
        )
    }
}

