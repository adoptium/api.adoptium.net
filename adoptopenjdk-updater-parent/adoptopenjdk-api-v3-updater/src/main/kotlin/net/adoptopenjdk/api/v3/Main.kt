package net.adoptopenjdk.api.v3

import org.jboss.weld.environment.se.Weld

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val container = Weld().containerId("STATIC_INSTANCE").initialize()
            val v3Updater = container.select(V3Updater::class.java).get()
            v3Updater.run(true)
        }
    }
}
