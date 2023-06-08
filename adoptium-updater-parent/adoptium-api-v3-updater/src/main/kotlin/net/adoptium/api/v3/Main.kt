package net.adoptium.api.v3

import net.adoptium.api.v3.ai.AppInsightsTelemetry
import org.jboss.weld.environment.se.Weld

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // Force eager App insights loading
            AppInsightsTelemetry.enabled

            val container = Weld().containerId("STATIC_INSTANCE").initialize()
            val v3Updater = container.select(V3Updater::class.java).get()
            v3Updater.run(true)
        }
    }
}
