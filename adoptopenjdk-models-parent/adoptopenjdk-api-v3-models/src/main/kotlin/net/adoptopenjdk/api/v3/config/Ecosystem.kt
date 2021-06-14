package net.adoptopenjdk.api.v3.config

import java.util.Properties

enum class Ecosystem {
    adoptopenjdk, adoptium;

    companion object {
        val CURRENT: Ecosystem

        init {
            val ecosystemFile = Ecosystem::class.java.getResourceAsStream("ecosystem.properties")
            val props = Properties()
            props.load(ecosystemFile)
            CURRENT = valueOf(props.getProperty("ecosystem"))
        }
    }
}
