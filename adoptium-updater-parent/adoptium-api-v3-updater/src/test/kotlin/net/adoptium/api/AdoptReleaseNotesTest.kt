package net.adoptium.api

import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import net.adoptium.api.v3.ReleaseIncludeFilter
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptium.api.v3.dataSources.HttpClientFactory
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHAssetsSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.releaseNotes.AdoptReleaseNotes
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
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

    @Test
    fun `incremental updates add and replace notes even when mapped releases are unchanged`() {
        runBlocking {
            val release = Release(
                "release-id", ReleaseType.ga, "release-link", "jdk-17.0.6+10",
                DateTime("2023-01-01T00:00:00Z"), DateTime("2023-01-01T00:00:00Z"),
                emptyArray(), 0, Vendor.eclipse, VersionData(17, 0, 6, null, 1, 10, null, "")
            )
            val repo = AdoptRepos(emptyList()).addAll(listOf(release))
            val id = GitHubId(release.id)
            val repository: AdoptRepository = mockk()
            val versionSupplier: VersionSupplier = mockk()
            val htmlClient: GitHubHtmlClient = mockk()
            val persistence = InMemoryApiPersistence(repo, mockk())
            val notes = AdoptReleaseNotes(repository, persistence, htmlClient)
            val builder = AdoptReposBuilder(repository, versionSupplier)
            val summary = GHReleaseSummary(id, "2023-01-01T00:00:00Z", "2023-01-01T00:00:00Z", release.release_name, GHAssetsSummary(1))
            coEvery { repository.getReleaseById(id) } returns ReleaseResult(listOf(release))
            coEvery { repository.getReleaseFilesForId(id) } returns listOf(GHAsset("release-notes.json", 0, "a-download-url", 0, ""))
            coEvery { htmlClient.getUrl("a-download-url") } returns "[]"

            for (updatedSummary in listOf(summary, summary.copy(updatedAt = "2023-01-02T00:00:00Z", releaseAssets = GHAssetsSummary(0)))) {
                coEvery { repository.getSummary(17) } returns GHRepositorySummary(GHReleasesSummary(listOf(updatedSummary), PageInfo(false, null)))
                val updated = builder.incrementalUpdate(
                    emptySet(), repo,
                    onReleasesUpdated = { notes.updateReleaseNotes(AdoptRepos(emptyList()).addAll(it)) },
                    gitHubMetadataSupplier = { GHReleaseMetadata(0, it) }
                )
                Assertions.assertEquals(repo, updated)
                Assertions.assertEquals(1, persistence.releaseNotes.size)
            }

            coVerify(exactly = 2) { htmlClient.getUrl("a-download-url") }

            coEvery { repository.getSummary(17) } returns GHRepositorySummary(GHReleasesSummary(listOf(summary.copy(releaseAssets = GHAssetsSummary(0))), PageInfo(false, null)))
            builder.incrementalUpdate(
                emptySet(), repo,
                onReleasesUpdated = { notes.updateReleaseNotes(AdoptRepos(emptyList()).addAll(it)) },
                gitHubMetadataSupplier = { GHReleaseMetadata(0, it) }
            )
            coVerify(exactly = 2) { htmlClient.getUrl("a-download-url") }
        }
    }

    @Test
    fun `existing release notes are refreshed`(adoptRepository: AdoptRepository) {
        runBlocking {
            val persistence = InMemoryApiPersistence(adoptRepos, mockk())
            val htmlClient: GitHubHtmlClient = mockk()
            coEvery { htmlClient.getUrl("a-download-url") } returns "[]"
            val notes = AdoptReleaseNotes(addReleaseNotesFiles(adoptRepository), persistence, htmlClient)
            notes.updateReleaseNotes(adoptRepos)
            val count = persistence.releaseNotes.size
            Assertions.assertTrue(count > 1)
            Assertions.assertTrue(persistence.releaseNotes.all { it.release_notes.isEmpty() })

            coEvery { htmlClient.getUrl("a-download-url") } coAnswers { gitHubHtmlClient().getUrl("a-download-url") }
            notes.updateReleaseNotes(adoptRepos)
            Assertions.assertEquals(count, persistence.releaseNotes.size)
            Assertions.assertTrue(persistence.releaseNotes.all { it.release_notes.size == 1 })
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
