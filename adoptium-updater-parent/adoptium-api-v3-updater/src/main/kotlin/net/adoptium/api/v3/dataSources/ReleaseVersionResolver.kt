package net.adoptium.api.v3.dataSources

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.models.ReleaseInfo
import net.adoptium.api.v3.models.ReleaseType

@ApplicationScoped
class ReleaseVersionResolver @Inject constructor(
    private val versionSupplier: VersionSupplier
) {

    fun formReleaseInfo(repo: AdoptRepos): ReleaseInfo {
        val gaReleases = repo
            .allReleases
            .getReleases()
            .filter { it.release_type == ReleaseType.ga }
            .toList()

        val availableReleases = gaReleases
            .map { it.version_data.major }
            .distinct()
            .sorted()
            .toList()
            .toTypedArray()
        val mostRecentFeatureRelease: Int = availableReleases.lastOrNull() ?: 0

        val ltsVersions = versionSupplier.getLtsVersions()

        val availableLtsReleases: Array<Int> = gaReleases
            .asSequence()
            .filter { ltsVersions.contains(it.version_data.major) }
            .map { it.version_data.major }
            .distinct()
            .sorted()
            .toList()
            .toTypedArray()
        val mostRecentLts = availableLtsReleases.lastOrNull() ?: 0

        val mostRecentFeatureVersion: Int = repo
            .allReleases
            .getReleases()
            .map { it.version_data.major }
            .distinct()
            .sorted()
            .lastOrNull() ?: 0

        val tip = versionSupplier.getTipVersion() ?: mostRecentFeatureVersion

        return ReleaseInfo(
            availableReleases,
            availableLtsReleases,
            mostRecentLts,
            mostRecentFeatureRelease,
            mostRecentFeatureVersion,
            tip
        )
    }
}
