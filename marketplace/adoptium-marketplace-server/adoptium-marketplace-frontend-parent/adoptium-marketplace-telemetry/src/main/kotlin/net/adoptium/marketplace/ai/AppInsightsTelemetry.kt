package net.adoptium.marketplace.ai

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

object AppInsightsTelemetry {

    val telemetryClient: TelemetryClient?
    val enabled: Boolean

    init {
        telemetryClient = if (hasKey()) {
            enabled = true
            loadTelemetryClient()
        } else {
            enabled = false
            null
        }
    }

    private fun hasKey() = System.getProperty("APPINSIGHTS_INSTRUMENTATIONKEY") != null || System.getenv("APPINSIGHTS_INSTRUMENTATIONKEY") != null

    fun start() {
        if (hasKey()) {
            LoggerFactory.getLogger(this::class.java).info("Started AppInsightsTelemetry")
        }
    }

    private fun loadTelemetryClient(): TelemetryClient {
        val path = saveConfigToFile()
        try {
            return TelemetryClient()
        } finally {
            val tmpDir = System.getProperty("java.io.tmpdir")
            val isTmpDir = path?.startsWith(tmpDir)
            if (isTmpDir != null && isTmpDir) {
                path.toFile().deleteRecursively()
            }
        }
    }

    // Unfortunate hack to get around that we seem unable to load ApplicationInsights.xml from the classpath
    // from inside the module
    private fun saveConfigToFile(): Path? {
        val inputStream = AppInsightsTelemetry::class.java.classLoader.getResourceAsStream("ApplicationInsights.xml")

        inputStream?.use {
            val configPath = Files.createTempDirectory("appInsightsConfig").toAbsolutePath()
            val configFile = File(configPath.toString(), "ApplicationInsights.xml")
            configFile.deleteOnExit()
            configPath.toFile().deleteOnExit()
            System.setProperty("applicationinsights.configurationDirectory", configPath.toString())

            val outputStream = FileOutputStream(configFile)

            outputStream.use { fileOut ->
                inputStream.copyTo(fileOut)
            }

            return configPath
        }
        return null
    }
}
