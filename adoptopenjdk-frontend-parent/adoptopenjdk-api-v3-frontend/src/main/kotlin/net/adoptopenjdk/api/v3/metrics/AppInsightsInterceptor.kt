package net.adoptopenjdk.api.v3.metrics

import com.microsoft.applicationinsights.telemetry.Duration
import com.microsoft.applicationinsights.telemetry.RequestTelemetry
import net.adoptopenjdk.api.v3.ai.AppInsightsTelemetry
import java.util.*
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider
import javax.ws.rs.ext.WriterInterceptor
import javax.ws.rs.ext.WriterInterceptorContext

private const val START_TIME_KEY = "appInsightsRequestStartTime"
private const val TELEMETERY_KEY = "appInsightsTelemetery"

private fun nanoToMilliseconds(time: Long): Double = time / 1000000.0

private fun millisecondsSince(startTime: Long): Double {
    return nanoToMilliseconds(System.nanoTime() - startTime)
}

private fun recordEvent(
    startTime: Long,
    requestTelemetry: RequestTelemetry
) {
    if (AppInsightsTelemetry.enabled) {
        val duration = millisecondsSince(startTime)
        requestTelemetry.metrics["writeTime"] = duration
        AppInsightsTelemetry.telemetryClient?.trackRequest(requestTelemetry)
    }
}

@Provider
class BeforeContainerRequest : ContainerRequestFilter {
    override fun filter(context: ContainerRequestContext?) {
        context?.setProperty(START_TIME_KEY, System.nanoTime())
    }
}

@Provider
class AfterContainerFilter : ContainerResponseFilter {
    override fun filter(requestContext: ContainerRequestContext?, responseContext: ContainerResponseContext?) {

        val startTime = requestContext?.getProperty(START_TIME_KEY)

        if (requestContext != null &&
            responseContext != null &&
            startTime != null &&
            startTime is Long
        ) {

            val duration = millisecondsSince(startTime)

            val requestTelemetry = RequestTelemetry(
                requestContext.uriInfo.path,
                Date(),
                duration.toLong(),
                responseContext.status.toString(),
                responseContext.status < 400
            )
            requestTelemetry.metrics["processingTime"] = duration

            val userAgent = requestContext.headers.getFirst("User-Agent")
            if (userAgent != null) {
                requestTelemetry.properties["User-Agent"] = userAgent
            }
            requestContext.setProperty(TELEMETERY_KEY, requestTelemetry)

            if (responseContext.entity == null && AppInsightsTelemetry.enabled) {
                recordEvent(startTime, requestTelemetry)
            }
        }
    }
}

@Provider
class Writer : WriterInterceptor {
    override fun aroundWriteTo(context: WriterInterceptorContext?) {
        if (AppInsightsTelemetry.enabled) {
            val startTime = context?.getProperty(START_TIME_KEY)
            val requestTelemetry = context?.getProperty(TELEMETERY_KEY)

            if (requestTelemetry != null && requestTelemetry is RequestTelemetry && startTime != null && startTime is Long) {
                val duration = millisecondsSince(startTime)
                requestTelemetry.duration = Duration(duration.toLong())
            }

            context?.proceed()

            if (requestTelemetry != null && requestTelemetry is RequestTelemetry && startTime != null && startTime is Long) {
                recordEvent(startTime, requestTelemetry)
            }
        } else {
            context?.proceed()
        }
    }
}
