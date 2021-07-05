package net.adoptopenjdk.api.v3.config

class APIConfig {
    companion object {
        val ENVIRONMENT: MutableMap<String, String> = HashMap(System.getenv())
        val DEBUG: Boolean = System.getenv("DEBUG")?.toBoolean() ?: false
    }
}
