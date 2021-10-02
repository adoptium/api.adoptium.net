package net.adoptopenjdk.api.v3.routes

import net.adoptopenjdk.api.v3.Updater
import javax.annotation.security.RolesAllowed
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

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
