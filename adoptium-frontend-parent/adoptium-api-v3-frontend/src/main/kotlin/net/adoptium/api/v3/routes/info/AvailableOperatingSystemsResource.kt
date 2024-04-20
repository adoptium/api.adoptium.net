package net.adoptium.api.v3.routes.info

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import net.adoptium.api.v3.models.OperatingSystem
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Release Info")
@Path("/v3/info")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AvailableOperatingSystemsResource {

    @GET
    @Path("/available/operating-systems")
    @Operation(summary = "Returns names of available operating systems", operationId = "getAvailableOperatingSystems")
    fun get(): List<String> {
        return OperatingSystem.values().map { it.name }.toList()
    }
}
