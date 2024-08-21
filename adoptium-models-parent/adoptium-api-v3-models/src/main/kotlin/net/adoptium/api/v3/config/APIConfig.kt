package net.adoptium.api.v3.config

class APIConfig {
    companion object {
        val ENVIRONMENT: MutableMap<String, String> = HashMap(System.getenv())
        val DEBUG: Boolean = System.getenv("DEBUG")?.toBoolean() ?: false

        var DISABLE_UPDATER: Boolean = System.getenv("DISABLE_UPDATER")?.toBoolean() ?: false

        var UPDATE_ADOPTOPENJDK: Boolean = System.getenv("UPDATE_ADOPTOPENJDK")?.toBoolean() ?: false

        // We will only update pre-releases if they are less than n days old
        var UPDATE_DAY_CUTOFF: Int = System.getenv("UPDATE_DAY_CUTOFF")?.toInt() ?: 90

        var DEPLOYMENT_TYPE: DeploymentType = DeploymentType.valueOf((System.getenv("DEPLOYMENT_TYPE") ?: "FRONTEND").uppercase())
    }
}
