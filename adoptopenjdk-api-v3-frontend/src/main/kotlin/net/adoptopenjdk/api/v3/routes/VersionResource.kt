package net.adoptopenjdk.api.v3.routes

import net.adoptopenjdk.api.v3.models.VersionData
import net.adoptopenjdk.api.v3.parser.FailedToParse
import net.adoptopenjdk.api.v3.parser.VersionParser
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import javax.ws.rs.PathParam
import org.slf4j.LoggerFactory
import javax.enterprise.context.ApplicationScoped
import javax.ws.rs.BadRequestException
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

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
        version: String?
    ): VersionData {
        try {
            return VersionParser.parse(version, sanityCheck = false, exactMatch = true)
        } catch (e: FailedToParse) {
            LOGGER.info("Failed to parse version: $version", e)
            throw BadRequestException("Unable to parse version")
        }
    }
}
