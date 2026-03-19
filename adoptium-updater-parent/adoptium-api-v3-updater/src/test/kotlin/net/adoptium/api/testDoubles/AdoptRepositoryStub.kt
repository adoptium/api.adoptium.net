package net.adoptium.api.testDoubles

import net.adoptium.api.v3.ReleaseIncludeFilter
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import jakarta.enterprise.inject.Produces
import net.adoptium.api.BaseTest
import net.adoptium.api.GHSummaryTestDataGenerator
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import org.jboss.weld.junit5.auto.ExcludeBean

@Priority(1)
@Alternative
@ApplicationScoped
open class AdoptRepositoryStub : AdoptRepository {
    @Produces
    @ExcludeBean
    open val repo = BaseTest.adoptRepos

    @Produces
    @ExcludeBean
    open val attestationRepo = BaseTest.adoptAttestationRepos

    open val toRemove = repo.getFeatureRelease(8)!!.releases.nodes.values.first()
    open val originalToUpdate = repo.getFeatureRelease(8)!!.releases.nodes.values.take(2).last()

    open val toUpdate = Release(
        originalToUpdate.id, ReleaseType.ga, "openjdk-8u", "jdk8u-2018-09-27-08-50",
        DateTime(TimeSource.now().minusDays(2).withSecond(0).withMinute(0)),
        DateTime(TimeSource.now().minusDays(2).withSecond(0).withMinute(0)),
        arrayOf(), 2, Vendor.adoptopenjdk,
        VersionData(8, 2, 3, "", 1, 4, "", "")
    )

    private val updated = repo
        .removeRelease(8, toRemove)
        .addRelease(8, toAdd)
        .addRelease(8, toAddYoung)
        .removeRelease(8, originalToUpdate)
        .addRelease(8, toUpdate)
        .addRelease(8, toAddSemiYoungRelease)

    companion object {
        const val unchangedIndex = 3

        val toAdd = Release(
            "foo", ReleaseType.ga, "openjdk-8u", "jdk8u-2018-09-27-08-50",
            DateTime(TimeSource.now().minusDays(2).withSecond(0).withMinute(0)),
            DateTime(TimeSource.now().minusDays(2).withSecond(0).withMinute(0)),
            arrayOf(), 2, Vendor.adoptopenjdk,
            VersionData(8, 2, 3, "", 1, 4, "", "")
        )

        val toAddSemiYoungRelease = Release(
            "semi-young", ReleaseType.ga, "openjdk-8u", "jdk8u-2018-09-27-08-50",
            DateTime(TimeSource.now().minusMinutes(20)),
            DateTime(TimeSource.now().minusMinutes(20)),
            arrayOf(), 2, Vendor.adoptopenjdk,
            VersionData(8, 2, 3, "", 1, 4, "", "")
        )

        val toAddYoung = Release(
            "young", ReleaseType.ga, "openjdk-8u", "jdk8u-2018-09-27-08-50",
            DateTime(TimeSource.now()),
            DateTime(TimeSource.now()), arrayOf(), 2, Vendor.adoptopenjdk,
            VersionData(8, 2, 3, "", 1, 4, "", "")
        )
    }

    override suspend fun getRelease(version: Int, filter: ReleaseIncludeFilter): FeatureRelease? {
        return updated.getFeatureRelease(version)
    }

    override suspend fun getSummary(version: Int): GHRepositorySummary {
        return GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(
                AdoptRepos(listOf(updated.getFeatureRelease(version)!!))
            )
        )
    }

    override suspend fun getReleaseById(gitHubId: GitHubId): ReleaseResult? {
        return updated
            .allReleases
            .getReleases()
            .filter {
                GitHubId(it.id) == gitHubId
            }
            .map {
                ReleaseResult(listOf(it))
            }
            .firstOrNull()
    }

    override suspend fun getReleaseFilesForId(gitHubId: GitHubId): List<GHAsset>? {
        return updated
            .allReleases
            .getReleases()
            .filter {
                GitHubId(it.id) == gitHubId
            }
            .flatMap {
                it
                    .binaries
                    .map {
                        GHAsset(
                            it.`package`.name,
                            it.`package`.size,
                            it.`package`.link,
                            it.`package`.download_count,
                            it.updated_at.dateTime.toString()
                        )
                    }
            }
            .toList()
    }
}
