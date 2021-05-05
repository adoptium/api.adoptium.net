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

object ServerConfig {
    const val SERVER = "https://api.adoptopenjdk.net"
}

const val DESCRIPTION =
    """
<li><strong>NOTICE:</strong> AdoptOpenJDK API v1 Has now been removed.</li>
<li><strong>NOTICE:</strong> AdoptOpenJDK API v2 Has now been deprecated.</li>
<li><strong>NOTICE:</strong> If you are using v2 please move to the v3 as soon as possible. Please raise any migration 
problems as an issue in the <a href=\"https://github.com/AdoptOpenJDK/openjdk-api-v3/issues/new\">v3 issue tracker</a>.
</li>
<li><strong>NOTICE:</strong> For v2 docs please refer to 
<a href=\"https://api.adoptopenjdk.net/README\">https://api.adoptopenjdk.net/README</a>.
</li>"""

@OpenAPIDefinition(
    servers = [
        Server(url = ServerConfig.SERVER),
        Server(url = "https://staging-api.adoptopenjdk.net")
    ],
    info = Info(title = "v3", version = "3.0.0", description = DESCRIPTION)
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
