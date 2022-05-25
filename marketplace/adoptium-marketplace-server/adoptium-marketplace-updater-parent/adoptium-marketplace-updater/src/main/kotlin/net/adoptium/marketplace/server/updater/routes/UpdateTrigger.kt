package net.adoptium.marketplace.server.updater.routes

import kotlinx.coroutines.runBlocking
import net.adoptium.marketplace.schema.ReleaseUpdateInfo
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.server.updater.Updater
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.slf4j.LoggerFactory
import javax.annotation.security.RolesAllowed
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.SecurityContext

@Path("/updateForVendor/")
@Produces(MediaType.TEXT_PLAIN)
class UpdateTrigger @Inject constructor(private var updater: Updater) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    @GET
    @Path("/{vendor}")
    @RolesAllowed("user")
    fun get(
        @PathParam("vendor")
        vendor: Vendor,

        @Parameter(hidden = true, required = false)
        @Context
        securityContext: SecurityContext
    ): Response {
        return runBlocking {
            try {
                if (!securityContext.isUserInRole(vendor.name)) {
                    LOGGER.warn("User ${securityContext.userPrincipal.name} attempted to update vendor ${vendor.name} ")

                    Response.status(404)
                        .entity("Update failed, contact support\n")
                        .build()
                } else {

                    val response = runUpdate(vendor)

                    if (response.errorMessage != null) {
                        Response.status(500)
                            .entity(response)
                            .build()
                    } else {
                        Response.status(200)
                            .entity(response)
                            .build()
                    }
                }
            } catch (e: Exception) {
                LOGGER.warn("Update failed for vendor ${vendor.name}", e)

                Response.status(500)
                    .entity("Update failed, contact support\n")
                    .build()
            }
        }
    }

    private suspend fun runUpdate(vendor: Vendor): ReleaseUpdateInfo {
        return updater.update(vendor)
    }
}
