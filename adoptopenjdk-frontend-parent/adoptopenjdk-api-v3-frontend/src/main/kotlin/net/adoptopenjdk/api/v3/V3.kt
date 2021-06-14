package net.adoptopenjdk.api.v3

import io.quarkus.runtime.Startup
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition
import org.eclipse.microprofile.openapi.annotations.info.Info
import org.eclipse.microprofile.openapi.annotations.servers.Server
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@OpenAPIDefinition(
    servers = [
        Server(url = ServerConfig.SERVER),
        Server(url = ServerConfig.STAGING_SERVER)
    ],
    info = Info(title = "v3", version = "3.0.0", description = ServerConfig.DESCRIPTION)
)
@ApplicationScoped
@ApplicationPath("/")
@Startup
class V3 : Application() {

    companion object {
        val ENABLE_PERIODIC_UPDATES: String = "enablePeriodicUpdates"
    }

    /**
     * Used to initialize the periodic update scheduler of [APIDataStore]
     */
    @Inject
    @PostConstruct
    fun schedulePeriodicUpdates(apiDataStore: APIDataStore) {
        // Eagerly fetch repo from db on app startup
        val enabled = System.getProperty(ENABLE_PERIODIC_UPDATES, "true")!!.toBoolean()

        if (enabled) {
            apiDataStore.getAdoptRepos()
            apiDataStore.schedulePeriodicUpdates()
        }
    }
}
