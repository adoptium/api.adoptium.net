package net.adoptium.api.v3.routes

import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.parser.FailedToParse
import net.adoptium.api.v3.parser.VersionParser
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import jakarta.ws.rs.PathParam
import org.slf4j.LoggerFactory
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Tag(name = "Version")
@Path("/v3/version/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class VersionResource {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    @GET
    @Path("/{version}")
    @Operation(
        operationId = "parseVersion",
        summary = "Parses a java version string",
        description = "Parses a java version string and returns that data in a structured format"
    )
    @APIResponses(
        value = [
            APIResponse(responseCode = "400", description = "bad input parameter")
        ]
    )
    fun parseVersion(
        @Parameter(name = "version", description = "Version", required = true)
        @PathParam("version")
        version: String
    ): VersionData {
        try {
            return VersionParser.parse(version, sanityCheck = false, exactMatch = true)
        } catch (e: FailedToParse) {
            LOGGER.info("Failed to parse version: $version", e)
            throw BadRequestException("Unable to parse version")
        }
    }
}
