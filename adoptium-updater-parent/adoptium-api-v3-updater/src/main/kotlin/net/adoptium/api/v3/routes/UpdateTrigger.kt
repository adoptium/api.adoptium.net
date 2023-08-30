package net.adoptium.api.v3.routes

import net.adoptium.api.v3.Updater
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.annotation.security.RolesAllowed

@Path("/updater/")
@Produces(MediaType.TEXT_PLAIN)
class UpdateTrigger @Inject constructor(private var updater: Updater) {

    @GET
    @Path("/{release_name}")
    @RolesAllowed("user")
    fun get(
        @PathParam("release_name")
        releaseName: String
    ): Response {
        val updating = updater.addToUpdate(releaseName)
        return if (updating.isNotEmpty()) {
            val response = "Updating: \n" +
                updating
                    .joinToString("\n") { release ->
                        release.release_name + " " + release.id
                    }

            Response.status(200)
                .entity(response)
                .build()
        } else {
            Response.status(404)
                .entity("Could not find release with name ${releaseName}\n")
                .build()
        }
    }
}
