package net.adoptopenjdk.api.v3

import io.quarkus.runtime.Startup
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import javax.annotation.PostConstruct
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Startup
class Startup
@Inject
constructor(private val apiDataStore: APIDataStore) {

    companion object {
        val ENABLE_PERIODIC_UPDATES: String = "enablePeriodicUpdates"
    }

    @Inject
    @PostConstruct
    fun schedulePeriodicUpdates() {
        // Eagerly fetch repo from db on app startup
        val enabled = System.getProperty(ENABLE_PERIODIC_UPDATES, "true")!!.toBoolean()

        if (enabled) {
            apiDataStore.getAdoptRepos()
            apiDataStore.schedulePeriodicUpdates()
        }
    }
}
