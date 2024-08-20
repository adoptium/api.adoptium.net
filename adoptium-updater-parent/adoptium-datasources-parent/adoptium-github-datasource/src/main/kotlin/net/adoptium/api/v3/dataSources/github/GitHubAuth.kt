package net.adoptium.api.v3.dataSources.github

import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.slf4j.LoggerFactory
import io.jsonwebtoken.Jwts
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kohsuke.github.GHAppInstallation
import org.kohsuke.github.GHAppInstallationToken
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder

class GitHubAuth {
    data class AuthInfo(val token: String, val type: AuthType, val expirationTime: Date?)
    enum class AuthType {
        APP, TOKEN
    }

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private var TOKEN: AuthInfo? = null
        private val appId = System.getenv("GITHUB_APP_ID")
        private val privateKey = System.getenv("GITHUB_APP_PRIVATE_KEY")
        private val installationId = System.getenv("GITHUB_APP_INSTALLATION_ID")
        private val mutex = Mutex()

        suspend fun getAuthenticationToken(): AuthInfo {
                return mutex.withLock {
                    // Detect if we are using a GitHub App
                    if (!appId.isNullOrEmpty() && !privateKey.isNullOrEmpty() && !installationId.isNullOrEmpty()) {
                        if (TOKEN == null || (TOKEN!!.expirationTime != null && TOKEN!!.expirationTime!!.before(Date()))) {
                            LOGGER.info("Using GitHub App for authentication")
                            LOGGER.info("Generating a new installation token")
                            val token = authenticateAsGitHubApp(appId, privateKey, installationId)
                            TOKEN = AuthInfo(token.token, AuthType.APP, token.expiresAt)
                        }
                    } else {
                        if (TOKEN == null) {
                            val token = readToken()
                            LOGGER.info("Using Personal Access Token for authentication")
                            TOKEN = AuthInfo(token, AuthType.TOKEN, null)
                        }
                    }
                    TOKEN!!
                }
        }

        private fun readToken(): String {
            var token = System.getenv("GITHUB_TOKEN")
            if (token.isNullOrEmpty()) {

                val userHome = System.getProperty("user.home")

                // e.g /home/foo/.adopt_api/token.properties
                val propertiesFile = File(userHome + File.separator + ".adopt_api" + File.separator + "token.properties")

                if (propertiesFile.exists()) {

                    val properties = Properties()
                    properties.load(Files.newInputStream(propertiesFile.toPath()))
                    token = properties.getProperty("token")
                }
            }
            if (token.isNullOrEmpty()) {
                LOGGER.error("Could not find GITHUB_TOKEN")
                throw FailedToAuthenticateException()
            }
            return token
        }

        private fun authenticateAsGitHubApp(appId: String, privateKey: String, installationId: String): GHAppInstallationToken {
            try {
                // Remove the first and last lines
                val sanitizedKey = privateKey
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), "")

                // Decode the Base64 encoded key
                val keyBytes = Base64.getDecoder().decode(sanitizedKey)

                // Generate the private key
                val keySpec = PKCS8EncodedKeySpec(keyBytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(keySpec)

                // Create and sign the JWT
                val nowMillis = System.currentTimeMillis()
                val jwtToken = Jwts.builder()
                    .issuer(appId)
                    .issuedAt(Date(nowMillis))
                    .expiration(Date(nowMillis + 60000)) // Token valid for 1 minute
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact()

                val gitHubApp: GitHub = GitHubBuilder().withJwtToken(jwtToken).build()
                val appInstallation: GHAppInstallation = gitHubApp.app.getInstallationById(installationId.toLong())
                return appInstallation.createToken().create()
            } catch (e: Exception) {
                LOGGER.error("Error authenticating as GitHub App", e)
                throw FailedToAuthenticateException()
            }
        }
    }

    class FailedToAuthenticateException : Exception("Failed to authenticate to GitHub")
}
