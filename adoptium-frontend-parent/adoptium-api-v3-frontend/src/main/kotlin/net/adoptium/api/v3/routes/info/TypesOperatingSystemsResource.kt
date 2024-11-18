package net.adoptium.api.v3.routes.info

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import net.adoptium.api.v3.models.OperatingSystem
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Types")
@Path("/v3/types")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class TypesOperatingSystemsResource {

    @GET
    @Path("/operating_systems")
    @Operation(summary = "Returns names of operating systems", operationId = "getOperatingSystems")
    fun get(): List<String> {
        return OperatingSystem.entries.map { it.name }.toList()
    }
}
