package net.adoptopenjdk.api.testDoubles

import net.adoptopenjdk.api.BaseTest
import net.adoptopenjdk.api.GHSummaryTestDataGenerator
import net.adoptopenjdk.api.v3.AdoptRepository
import net.adoptopenjdk.api.v3.ReleaseResult
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId
import net.adoptopenjdk.api.v3.models.DateTime
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.models.VersionData
import org.jboss.weld.junit5.auto.ExcludeBean
import javax.annotation.Priority
import javax.enterprise.inject.Alternative
import javax.enterprise.inject.Produces
import javax.inject.Singleton

@Priority(1)
@Alternative
@Singleton
open class AdoptRepositoryStub : AdoptRepository {
    @Produces
    @ExcludeBean
    val repo = BaseTest.adoptRepos
    val toRemove = repo.getFeatureRelease(8)!!.releases.nodes.values.first()
    val originaToUpdate = repo.getFeatureRelease(8)!!.releases.nodes.values.take(2).last()

    val toUpdate = Release(
        originaToUpdate.id, ReleaseType.ga, "openjdk-8u", "jdk8u-2018-09-27-08-50",
        DateTime(TimeSource.now().minusDays(2)),
        DateTime(TimeSource.now().minusDays(2)),
        arrayOf(), 2, Vendor.adoptopenjdk,
        VersionData(8, 2, 3, "", 1, 4, "", "")
    )

    private val updated = repo
        .removeRelease(8, toRemove)
        .addRelease(8, toAdd)
        .addRelease(8, toAddYoung)
        .removeRelease(8, originaToUpdate)
        .addRelease(8, toUpdate)
        .addRelease(8, toAddSemiYoungRelease)

    companion object {
        val toAdd = Release(
            "foo", ReleaseType.ga, "openjdk-8u", "jdk8u-2018-09-27-08-50",
            DateTime(TimeSource.now().minusDays(2)),
            DateTime(TimeSource.now().minusDays(2)),
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

    override suspend fun getRelease(version: Int): FeatureRelease? {
        return updated.getFeatureRelease(version)
    }

    override suspend fun getSummary(version: Int): GHRepositorySummary {
        return GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(
                AdoptRepos(listOf(updated.getFeatureRelease(version)!!))
            )
        )
    }

    override suspend fun getReleaseById(id: GitHubId): ReleaseResult? {
        return updated
            .allReleases
            .getReleases()
            .filter {
                GitHubId(it.id) == id
            }
            .map {
                ReleaseResult(listOf(it))
            }
            .firstOrNull()
    }
}
