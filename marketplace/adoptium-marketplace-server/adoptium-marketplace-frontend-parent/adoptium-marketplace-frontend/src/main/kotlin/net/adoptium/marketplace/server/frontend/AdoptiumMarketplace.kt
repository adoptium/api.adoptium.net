package net.adoptium.marketplace.server.frontend

import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.runtime.Startup
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.schema.Vendor
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition
import org.eclipse.microprofile.openapi.annotations.info.Info
import org.eclipse.microprofile.openapi.annotations.servers.Server
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@UnlessBuildProfile("test")
@ApplicationScoped
@Startup
class KickOffUpdate @Inject constructor(
    apiDataStore: APIDataStore
) {
    init {
        runBlocking {
            delay(10000)
            //Warm up database
            Vendor
                .values()
                .forEach { vendor ->
                    apiDataStore.getReleases(vendor).getAllReleases()
                }
        }
    }
}


@OpenAPIDefinition(
    servers = [
        Server(url = ServerConfig.SERVER),
        Server(url = ServerConfig.STAGING_SERVER)
    ],
    info = Info(title = "AdoptiumMarketplace", version = "1.0.0", description = ServerConfig.DESCRIPTION)
)
@UnlessBuildProfile("test")
@ApplicationScoped
@ApplicationPath("/")
@Startup
class AdoptiumMarketplace : Application()
