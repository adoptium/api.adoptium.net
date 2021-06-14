package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.testDoubles.InMemoryApiPersistence
import net.adoptopenjdk.api.v3.dataSources.ReleaseVersionResolver
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UrlRequest
import net.adoptopenjdk.api.v3.models.ReleaseInfo
import org.apache.http.HttpResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ReleaseVersionResolverTest : BaseTest() {

    private fun getReleaseVersionResolver(
        apiPersistence: InMemoryApiPersistence = InMemoryApiPersistence(adoptRepos)
    ): ReleaseVersionResolver {
        return ReleaseVersionResolver(

            object : UpdaterHtmlClient {
                override suspend fun get(url: String): String? {
                    return getTipMetadata(url)
                }

                fun getTipMetadata(url: String): String {
                    return """
                        DEFAULT_VERSION_FEATURE=15
                        DEFAULT_VERSION_INTERIM=0
                    """.trimIndent()
                }

                override suspend fun getFullResponse(request: UrlRequest): HttpResponse? {
                    return null
                }
            }

        )
    }

    @Test
    fun availableVersionsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.available_releases.contentEquals(AdoptReposTestDataGenerator.generate().repos.keys.toTypedArray())
        }
    }

    @Test
    fun availableLtsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.available_lts_releases.contentEquals(arrayOf(8, 11))
        }
    }

    @Test
    fun mostRecentLtsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_lts == 11
        }
    }

    @Test
    fun mostRecentFeatureReleaseIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_feature_release == 12
        }
    }

    @Test
    fun mostRecentFeatureVersionIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_feature_version == 12
        }
    }

    private fun check(matcher: (ReleaseInfo) -> Boolean) {
        runBlocking {
            val releaseVersionResolver = getReleaseVersionResolver()
            val info = releaseVersionResolver.formReleaseInfo(adoptRepos)
            assertTrue(matcher(info))
        }
    }

    @Test
    fun tipVersionIsCorrect() {
        check { releaseInfo ->
            releaseInfo.tip_version == 15
        }
    }
}
