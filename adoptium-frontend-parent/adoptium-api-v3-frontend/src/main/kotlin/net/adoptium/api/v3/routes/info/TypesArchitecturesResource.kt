package net.adoptium.api.v3.routes.info

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import net.adoptium.api.v3.models.Architecture
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Types")
@Path("/v3/types")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class TypesArchitecturesResource {

    @GET
    @Path("/architectures")
    @Operation(summary = "Returns names of architectures", operationId = "getArchitectures")
    fun get(): List<String> {
        return Architecture.entries.map { it.name }.toList()
    }
}
