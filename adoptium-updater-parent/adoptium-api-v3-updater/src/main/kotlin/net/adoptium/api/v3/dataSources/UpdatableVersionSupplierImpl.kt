package net.adoptium.api.v3.dataSources

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@ApplicationScoped
class UpdatableVersionSupplierImpl @Inject constructor(val updaterHtmlClient: UpdaterHtmlClient) : VersionSupplier, UpdatableVersionSupplier {
    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(UpdatableVersionSupplierImpl::class.java)
    }

    private val DEFAULT_LATEST_JAVA_VERSION = 24
    private var DEFAULT_LTS_JAVA_VERSIONS: Array<Int> = arrayOf(8, 11, 17, 21, 25)

    private val LATEST_JAVA_VERSION_PROPERTY = "LATEST_JAVA_VERSION"
    private val LTS_JAVA_VERSIONS_PROPERTY = "LTS_JAVA_VERSIONS"

    private val VERSION_FILE_URL = "https://raw.githubusercontent.com/openjdk/jdk/master/make/conf/version-numbers.conf"

    private var tipVersion: Int? = null
    private var latestJavaVersion: Int
    private var versions: Array<Int>
    private var ltsVersions: Array<Int> = arrayOf(8, 11, 17, 21, 25)

    init {

        var lts = System.getProperty(LTS_JAVA_VERSIONS_PROPERTY, null)

        ltsVersions = if (lts != null) {
            try {
                val ltsList = lts.split(",").map { it.trim().toInt() }
                if (ltsList.isEmpty()) {
                    LOGGER.error("LTS versions must be non-empty")
                    DEFAULT_LTS_JAVA_VERSIONS
                } else {
                    ltsList.toTypedArray()
                }
            } catch (e: NumberFormatException) {
                LOGGER.warn("Invalid LTS_JAVA_VERSIONS property, using default LTS versions", e)
                DEFAULT_LTS_JAVA_VERSIONS
            }
        } else {
            DEFAULT_LTS_JAVA_VERSIONS
        }

        latestJavaVersion = Integer.parseInt(System.getProperty(LATEST_JAVA_VERSION_PROPERTY, DEFAULT_LATEST_JAVA_VERSION.toString()))
        versions = (8..latestJavaVersion).toList().toTypedArray()
        runBlocking {
            updateVersions()
        }
    }

    override suspend fun updateVersions() {
        try {
            val versionFile = updaterHtmlClient.get(VERSION_FILE_URL)

            if (versionFile != null) {
                tipVersion = Regex(""".*DEFAULT_VERSION_FEATURE=(?<num>\d+).*""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
                    .matchEntire(versionFile)?.groups?.get("num")?.value?.toInt()
            } else {
                LOGGER.warn("Failed to get tip version")
            }


            if (tipVersion != null && tipVersion!! > latestJavaVersion) {
                latestJavaVersion = tipVersion as Int
                versions = (8..latestJavaVersion).toList().toTypedArray()
            }

        } catch (e: Exception) {
            LOGGER.warn("Failed to get tip version", e)
        }
    }

    override fun getTipVersion(): Int? {
        return this.tipVersion
    }

    override fun getLtsVersions(): Array<Int> {
        return ltsVersions
    }

    override fun getAllVersions(): Array<Int> {
        return versions
    }
}
