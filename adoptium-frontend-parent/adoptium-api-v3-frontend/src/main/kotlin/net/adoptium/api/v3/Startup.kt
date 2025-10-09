package net.adoptium.api.v3

import io.quarkus.runtime.Startup
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.config.DeploymentType
import net.adoptium.api.v3.dataSources.APIDataStore

@ApplicationScoped
@Startup
class Startup
@Inject
constructor(private val apiDataStore: APIDataStore) {

    companion object {
        const val ENABLE_PERIODIC_UPDATES: String = "enablePeriodicUpdates"
    }

    @Inject
    @PostConstruct
    fun schedulePeriodicUpdates() {
        // Eagerly fetch repo from db on app startup
        val enabled = System.getProperty(ENABLE_PERIODIC_UPDATES, "true")!!.toBoolean()

        if (enabled) {
            apiDataStore.getAdoptRepos()
            apiDataStore.getAdoptAttestationRepos()
            apiDataStore.schedulePeriodicUpdates()
        }
    }
}
