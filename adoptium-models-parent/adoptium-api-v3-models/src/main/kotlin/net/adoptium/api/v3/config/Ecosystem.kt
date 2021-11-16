package net.adoptium.api.v3.config

import net.adoptium.api.v3.models.Vendor
import java.util.Properties

enum class Ecosystem {
    adoptopenjdk, adoptium;

    companion object {
        val CURRENT: Ecosystem

        init {
            val ecosystemFile = Ecosystem::class.java.getResourceAsStream("ecosystem.properties")
            val props = Properties()
            props.load(ecosystemFile)
            CURRENT = if (props.contains("ecosystem")) {
                valueOf(props.getProperty("ecosystem"))
            } else if (Vendor.getDefault() == Vendor.adoptium || Vendor.getDefault() == Vendor.eclipse) {
                adoptium
            } else if (Vendor.getDefault() == Vendor.adoptopenjdk) {
                adoptopenjdk
            } else {
                throw RuntimeException("Unable to detect ecosystem")
            }
        }
    }
}
