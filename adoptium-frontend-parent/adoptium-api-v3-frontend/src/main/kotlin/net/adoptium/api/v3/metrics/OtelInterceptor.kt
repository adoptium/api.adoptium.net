package net.adoptium.api.v3.metrics

import io.opentelemetry.api.trace.Span
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.container.ContainerResponseFilter
import javax.ws.rs.ext.Provider

@Provider
class AfterContainerFilter : ContainerResponseFilter {
    override fun filter(requestContext: ContainerRequestContext?, responseContext: ContainerResponseContext?) {
        try {
            val userAgent = requestContext?.headers?.get("User-Agent")?.getOrNull(0)
            val span: Span? = Span.current()
            if (userAgent != null && span != null) {
                span.setAttribute("User-Agent", userAgent)
            }
        } catch (_: Exception) {
            //Ignore
        }
    }
}
