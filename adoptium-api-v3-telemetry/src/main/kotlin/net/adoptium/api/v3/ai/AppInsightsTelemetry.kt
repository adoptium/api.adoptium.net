package net.adoptium.api.v3.ai

import com.microsoft.applicationinsights.attach.ApplicationInsights
import org.slf4j.LoggerFactory

object AppInsightsTelemetry {

    val enabled: Boolean

    init {
        if (hasKey()) {
            enabled = true
            start()
        } else {
            enabled = false
        }
    }


    private fun hasKey() =
        System.getProperty("APPINSIGHTS_INSTRUMENTATIONKEY") != null
            || System.getenv("APPINSIGHTS_INSTRUMENTATIONKEY") != null
            || System.getProperty("APPLICATIONINSIGHTS_CONNECTION_STRING") != null
            || System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING") != null

    fun start() {
        if (hasKey()) {
            ApplicationInsights.attach()
            LoggerFactory.getLogger(this::class.java).info("Started AppInsightsTelemetry")
        }
    }
}
