package net.adoptium.api

import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHAssetsSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.models.ReleaseType
import java.time.format.DateTimeFormatter

object GHSummaryTestDataGenerator {

    fun generateGHRepository(adoptRepos: AdoptRepos): GHRepository {
        return GHRepository(
            GHReleases(
                adoptRepos
                    .allReleases
                    .getReleases()
                    .map {
                        val url = "/AdoptOpenJDK/openjdk${it.version_data.major}-binaries/releases/tag/jdk8u-2020-01-09-03-36"
                        GHRelease(
                            GitHubId(it.id),
                            it.release_name,
                            it.release_type == ReleaseType.ea,
                            DateTimeFormatter.ISO_INSTANT.format(it.timestamp.dateTime),
                            DateTimeFormatter.ISO_INSTANT.format(it.updated_at.dateTime),
                            GHAssets(
                                it.binaries
                                    .map { asset ->
                                        GHAsset(
                                            asset.`package`.name,
                                            asset.`package`.size,
                                            asset.`package`.link,
                                            asset.`package`.download_count,
                                            DateTimeFormatter.ISO_INSTANT.format(asset.updated_at.dateTime)
                                        )
                                    },
                                PageInfo(false, null),
                                it.binaries.size
                            ),
                            it.id,
                            url
                        )
                    }
                    .toList(),
                PageInfo(false, null)
            )
        )
    }

    fun generateGHRepositorySummary(repo: GHRepository): GHRepositorySummary {
        return GHRepositorySummary(
            GHReleasesSummary(
                repo
                    .getReleases()
                    .map { release ->
                        GHReleaseSummary(
                            release.id,
                            release.publishedAt,
                            release.updatedAt,
                            release.name,
                            GHAssetsSummary(release.releaseAssets.totalCount)
                        )
                    },
                PageInfo(false, null)
            )
        )
    }
}
