package net.adoptium.api.v3.routes.info

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.OperatingSystem
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Types")
@Path("/v3/types")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class TypesResource {

    @GET
    @Path("/operating_systems")
    @Operation(summary = "Returns names of operating systems", operationId = "getOperatingSystems")
    fun getOperatingSystems(): List<String> {
        return OperatingSystem.entries.map { it.name }.toList()
    }

    @GET
    @Path("/architectures")
    @Operation(summary = "Returns names of architectures", operationId = "getArchitectures")
    fun getArchitectures(): List<String> {
        return Architecture.entries.map { it.name }.toList()
    }
}
