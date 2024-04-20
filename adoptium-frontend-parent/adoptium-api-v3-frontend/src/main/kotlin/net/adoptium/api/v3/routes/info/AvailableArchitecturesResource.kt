package net.adoptium.api.v3.routes.info

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import net.adoptium.api.v3.models.Architecture
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Release Info")
@Path("/v3/info")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AvailableArchitecturesResource {

    @GET
    @Path("/available/architectures")
    @Operation(summary = "Returns names of available architectures", operationId = "getAvailableArchitectures")
    fun get(): List<String> {
        return Architecture.values().map { it.name }.toList()
    }
}
