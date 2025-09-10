package net.adoptium.api

import io.mockk.mockk
import net.adoptium.api.v3.ReleaseIncludeFilter
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptium.api.v3.dataSources.HttpClientFactory
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.releaseNotes.AdoptReleaseNotes
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@AddPackages(value = [DefaultUpdaterHtmlClient::class, HttpClientFactory::class])
class AdoptReleaseNotesTest : BaseTest() {
    @Test
    fun releaseNotesAreAdded(adoptRepository: AdoptRepository) {

        val modifiedRepo = addReleaseNotesFiles(adoptRepository)

        val persistence = InMemoryApiPersistence(adoptRepos, mockk())

        val adoptReleaseNotes = AdoptReleaseNotes(
            modifiedRepo,
            persistence,
            gitHubHtmlClient()
        )

        runBlocking {
            Assertions.assertEquals(0, persistence.releaseNotes.size)
            adoptReleaseNotes.updateReleaseNotes(adoptRepos)
            Assertions.assertTrue(persistence.releaseNotes.size > 1)
        }
    }

    private fun gitHubHtmlClient() = object : GitHubHtmlClient {
        override suspend fun getUrl(url: String): String? {
            if (url == "a-download-url") {
                return """
                                [
                                  {
                                    "id": "JDK-8290974",
                                    "title": "8290974: Bump version numbers for January 2023 CPU",
                                    "priority": null,
                                    "component": null,
                                    "subcomponent": null,
                                    "link": "https://bugs.openjdk.java.net/browse/JDK-8290974",
                                    "type": null,
                                    "backportOf": null
                                  }
                                ]
                            """.trimIndent()
            } else {
                return null
            }
        }

    }

    private fun addReleaseNotesFiles(adoptRepository: AdoptRepository) = object : AdoptRepository {
        override suspend fun getRelease(version: Int, filter: ReleaseIncludeFilter): FeatureRelease? {
            return adoptRepository.getRelease(version, filter)
        }

        override suspend fun getSummary(version: Int): GHRepositorySummary {
            return adoptRepository.getSummary(version)
        }

        override suspend fun getReleaseById(gitHubId: GitHubId): ReleaseResult? {
            return adoptRepository.getReleaseById(gitHubId)
        }

        override suspend fun getReleaseFilesForId(gitHubId: GitHubId): List<GHAsset>? {
            return adoptRepository
                .getReleaseFilesForId(gitHubId)
                ?.plus(GHAsset(
                    gitHubId.id + ".a-release-notes.json",
                    0L,
                    "a-download-url",
                    0,
                    ""
                )
                )
        }
    }
}
