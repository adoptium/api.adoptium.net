package net.adoptopenjdk.api

import net.adoptopenjdk.api.v3.dataSources.github.GitHubAuth
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.oneOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * GitHubAuth makes actual calls to System.getenv and System.getProperty, so ignore these tests if an
 * env var or property named GITHUB_TOKEN exists to prevent real tokens being inadvertently logged as assertion errors.
 */
@DisabledIfEnvironmentVariable(named = GitHubAuthTest.tokenKey, matches = ".*")
@DisabledIfSystemProperty(named = GitHubAuthTest.tokenKey, matches = ".*")
class GitHubAuthTest {

    companion object {
        const val tokenKey = "GITHUB_TOKEN"
        private lateinit var tempDir: File
        private lateinit var tokenFile: File
        private lateinit var savedUserHome: String

        @JvmStatic @BeforeAll
        fun setUpClass() {
            tempDir = Files.createTempDirectory("testUserHome").toFile()

            savedUserHome = System.getProperty("user.home")
            System.setProperty("user.home", tempDir.absolutePath)
        }

        @JvmStatic @AfterAll
        fun cleanUp() {
            tempDir.deleteRecursively()
            System.setProperty("user.home", savedUserHome)
        }
    }

    @BeforeEach
    fun setUp() {
        assertFalse(System.getenv().containsKey(tokenKey))
        assertFalse(System.getProperties().containsKey(tokenKey))
    }

    @Test
    fun `readToken prioritizes system property if env var is not defined`() {
        assertFalse(System.getenv().containsKey(tokenKey))

        val prevTokenProperty: String? = System.getProperty(tokenKey)
        System.setProperty(tokenKey, "system-property-token")

        try {
            val actualToken = GitHubAuth.readToken()
            assertEquals("system-property-token", actualToken)
        } finally {
            if (prevTokenProperty == null) {
                System.clearProperty(tokenKey)
            } else {
                System.setProperty(tokenKey, prevTokenProperty)
            }
        }
    }

    @Test
    fun `readToken falls back to property file if env var and system property are not defined`() {
        assertFalse(System.getenv().containsKey(tokenKey))
        assertFalse(System.getProperties().containsKey(tokenKey))

        val tokenDir = File(tempDir, ".adopt_api")
        tokenDir.mkdirs()
        tokenFile = File(tokenDir, "token.properties")
        tokenFile.printWriter().use { out ->
            out.println("token=real-file-token")
        }

        try {
            val actualToken = GitHubAuth.readToken()
            assertEquals("real-file-token", actualToken)
        } finally {
            tokenDir.deleteRecursively()
        }
    }

    @Test
    fun readsTokenNullFromFile() {
        assertFalse(System.getenv().containsKey(tokenKey))
        assertFalse(File(tempDir, ".adopt_api").exists())

        val actualToken = GitHubAuth.readToken()
        assertThat(actualToken, oneOf(null, ""))
    }
}
