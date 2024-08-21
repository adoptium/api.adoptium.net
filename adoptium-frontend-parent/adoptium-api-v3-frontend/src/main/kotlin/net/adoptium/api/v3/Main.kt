package net.adoptium.api.v3

import io.quarkus.runtime.Quarkus

import io.quarkus.runtime.annotations.QuarkusMain
import net.adoptium.api.v3.ai.AppInsightsTelemetry
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.config.DeploymentType


@QuarkusMain
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        // force eager startup of AppInsights, must be done from the main thread
        AppInsightsTelemetry.enabled
        APIConfig.DEPLOYMENT_TYPE = DeploymentType.FRONTEND

        Quarkus.run(*args)
    }
}
