package net.adoptium.api.v3.metrics

import io.opentelemetry.api.trace.Span
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider

@Provider
class AfterContainerFilter : ContainerResponseFilter {
    // Fetch the hostname once and cache it
    private val hostname: String = System.getenv("HOSTNAME") ?: "unknown"

    override fun filter(requestContext: ContainerRequestContext?, responseContext: ContainerResponseContext?) {
        try {
            // Existing User-Agent code
            val userAgent = requestContext?.headers?.get("User-Agent")?.getOrNull(0)
            val span: Span? = Span.current()
            if (userAgent != null && span != null) {
                span.setAttribute("User-Agent", userAgent)
            }

            // Add the pod's hostname as a response header
            responseContext?.headers?.add("X-Pod-Hostname", hostname)

        } catch (_: Exception) {
            //Ignore
        }
    }
}
