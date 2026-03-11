package com.media3watch.api

import com.media3watch.config.ApiConstants
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Route.metricsRoutes(registry: PrometheusMeterRegistry) {
    get(ApiConstants.Routes.METRICS) {
        call.respondText(
            registry.scrape(),
            ContentType.parse(ApiConstants.Observability.PROMETHEUS_CONTENT_TYPE)
        )
    }
}
