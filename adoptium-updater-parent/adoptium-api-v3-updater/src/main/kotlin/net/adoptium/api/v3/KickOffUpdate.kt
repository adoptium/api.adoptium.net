package net.adoptium.api.v3

import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.config.APIConfig

@UnlessBuildProfile("test")
@ApplicationScoped
@Startup
class KickOffUpdate @Inject constructor(
    v3Updater: V3Updater
) {
    init {
        if (!APIConfig.DISABLE_UPDATER) {
            v3Updater.run(true)
        }
    }
}
