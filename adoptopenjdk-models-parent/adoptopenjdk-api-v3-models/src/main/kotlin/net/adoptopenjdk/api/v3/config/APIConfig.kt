package net.adoptopenjdk.api.v3.config

class APIConfig {
    companion object {
        val DEBUG: Boolean = System.getenv("DEBUG")?.toBoolean() ?: false
    }
}
