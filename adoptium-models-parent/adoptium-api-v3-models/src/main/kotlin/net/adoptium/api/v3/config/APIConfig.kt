package net.adoptium.api.v3.config

class APIConfig {
    companion object {
        val ENVIRONMENT: MutableMap<String, String> = HashMap(System.getenv())
        val DEBUG: Boolean = System.getenv("DEBUG")?.toBoolean() ?: false

        var DISABLE_UPDATER: Boolean = System.getenv("DISABLE_UPDATER")?.toBoolean() ?: false

        var UPDATE_ADOPTOPENJDK: Boolean = System.getenv("UPDATE_ADOPTOPENJDK")?.toBoolean() ?: false
    }
}
