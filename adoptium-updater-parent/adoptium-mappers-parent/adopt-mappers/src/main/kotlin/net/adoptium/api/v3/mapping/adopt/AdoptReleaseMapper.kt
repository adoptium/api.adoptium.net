package net.adoptium.api.v3.mapping.adopt

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.mapping.BinaryMapper
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseNotesPackage
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.SourcePackage
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.parser.FailedToParse
import net.adoptium.api.v3.parser.VersionParser
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.*
import java.util.regex.Pattern

@ApplicationScoped
open class AdoptReleaseMapperFactory @Inject constructor(
    val adoptBinaryMapper: AdoptBinaryMapper,
    val htmlClient: GitHubHtmlClient
) {
    // Mappers hold no mutable state, so they are created on demand and never shared for their state.
    open fun get(vendor: Vendor): ReleaseMapper = AdoptReleaseMapper(adoptBinaryMapper, htmlClient, vendor)
}

private class AdoptReleaseMapper(
    val adoptBinaryMapper: AdoptBinaryMapper,
    val htmlClient: GitHubHtmlClient,
    val vendor: Vendor
) : ReleaseMapper() {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun toAdoptRelease(ghRelease: GHRelease): ReleaseResult {
        if (ghRelease.releaseAssets.pageInfo.hasNextPage) {
            LOGGER.error("Incomplete asset listing for ${ghRelease.id.id}")
            return ReleaseResult(error = "Incomplete asset listing", retryable = true)
        }

        val releaseType: ReleaseType = formReleaseType(ghRelease)

        val releaseLink = ghRelease.url
        val releaseName = ghRelease.name
        val timestamp = parseDate(ghRelease.publishedAt)
        val updatedAt = parseDate(ghRelease.updatedAt)

        val supportedAssets = ghRelease.releaseAssets.assets.filter {
            isReleaseAsset(it) && adoptBinaryMapper.isSupportedBinaryAsset(it)
        }
        val ghAssetsWithMetadata = try {
            associateMetadataWithBinaries(ghRelease.releaseAssets, supportedAssets)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Required sidecars must form a complete split snapshot. Do not
            // exclude the release permanently: a later refresh can retry it.
            LOGGER.error("Failed to retrieve metadata for $releaseName", e)
            return ReleaseResult(error = "Failed to retrieve metadata for $releaseName", retryable = true)
        }
        val selectedAssets = supportedAssets.filter { !it.name.contains("-sources") || it in ghAssetsWithMetadata }
        val ungroupedDownloads = downloadCount(ghRelease.releaseAssets.assets.filterNot { it in selectedAssets })
        val sourcePackage = getSourcePackage(ghRelease, ghAssetsWithMetadata)
        val releaseNotes = getReleaseNotesPackage(ghRelease.releaseAssets.assets)
        val aqavitResultsLink = getAqavitLink(ghRelease.releaseAssets.assets)

        try {
            val ghAssetsGroupedByVersion = ghAssetsWithMetadata
                .entries
                .filter { it.key in selectedAssets }
                .groupBy(this@AdoptReleaseMapper::getReleaseVersion)

            val releases = ghAssetsGroupedByVersion
                .entries
                .sortedBy { group -> group.value.maxOf { it.value.version.toApiVersion() } }
                .mapIndexed { index, ghAssetsForVersion ->
                    val version = ghAssetsForVersion.value.maxOf { it.value.version.toApiVersion() }

                    val ghAssets: List<GHAsset> = ghAssetsForVersion.value.map { ghAssetWithMetadata -> ghAssetWithMetadata.key }
                    val id = generateIdForSplitRelease(version, ghRelease)

                    // Attribute ungrouped downloads to the lowest version once,
                    // independent of the order GitHub returns its assets.
                    toRelease(releaseName, ghAssets, ghAssetsWithMetadata, id, releaseType, releaseLink, timestamp, updatedAt, vendor, version, ghRelease.releaseAssets.assets, sourcePackage, releaseNotes, aqavitResultsLink, if (index == 0) ungroupedDownloads else 0)
                }
                .ifEmpty {
                    val version = VersionParser.parse(releaseName)
                    listOf(toRelease(releaseName, selectedAssets, ghAssetsWithMetadata, ghRelease.id.id, releaseType, releaseLink, timestamp, updatedAt, vendor, version, ghRelease.releaseAssets.assets, sourcePackage, releaseNotes, aqavitResultsLink, ungroupedDownloads))
                }

            return ReleaseResult(result = releases.filterNot(::excludeRelease))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FailedToParse) {
            LOGGER.error("Failed to parse $releaseName", e)
            return ReleaseResult(error = "Failed to parse $releaseName")
        } catch (e: Exception) {
            LOGGER.error("Failed to map $releaseName", e)
            return ReleaseResult(error = "Failed to map $releaseName", retryable = true)
        }
    }

    private fun getAqavitLink(assets: List<GHAsset>): String? {
        val aqavitAssets = assets
            .filter { it.name.contains("AQAvitTapFiles") }
            .map { it.downloadUrl }
            .toList()

        if (aqavitAssets.size > 1) {
            LOGGER.warn("Multiple AqaVit assets present on release")
        }

        return aqavitAssets.firstOrNull()
    }

    private fun getReleaseVersion(ghAssetWithMetadata: Map.Entry<GHAsset, GHMetaData>): String {
        val version = ghAssetWithMetadata.value.version
        return "${version.major}.${version.minor}.${version.security}.${version.build}.${version.adopt_build_number}.${version.pre}"
    }

    private fun excludeRelease(release: Release): Boolean {
        return release.release_type == ReleaseType.ea && (
            // remove all 14.0.1+7.1 and 15.0.0+24.1 nightlies - https://github.com/AdoptOpenJDK/openjdk-api-v3/issues/213
            // also ignore jdk-2021-01-13-07-01
            release.version_data.semver.startsWith("14.0.1+7.1.") ||
                release.version_data.semver.startsWith("15.0.0+24.1.") ||
                release.release_name == "jdk-2021-01-13-07-01" ||
                release.release_name == "jdk17u-2022-05-27-19-32-beta"
            )
    }

    private fun generateIdForSplitRelease(version: VersionData, release: GHRelease): String {
        // using a shortend hash as a suffix to keep id short, probability of clash still very low
        val suffix = Base64
            .getEncoder()
            .encodeToString(
                MessageDigest
                    .getInstance("SHA-1")
                    .digest(version.semver.toByteArray())
                    .copyOfRange(0, 10)
            )

        return release.id.id + "." + suffix
    }

    private suspend fun toRelease(
        releaseName: String,
        ghAssets: List<GHAsset>,
        ghAssetWithMetadata: Map<GHAsset, GHMetaData>,
        id: String,
        release_type: ReleaseType,
        releaseLink: String,
        timestamp: ZonedDateTime,
        updatedAt: ZonedDateTime,
        vendor: Vendor,
        version: VersionData,
        fullGhAssetList: List<GHAsset>,
        sourcePackage: SourcePackage?,
        releaseNotes: ReleaseNotesPackage?,
        aqavitResultsLink: String?,
        ungroupedDownloads: Long
    ): Release {
        LOGGER.debug("Getting binaries $releaseName")
        val mapped = adoptBinaryMapper.mapBinaries(ghAssets, fullGhAssetList, ghAssetWithMetadata)
        check(mapped.size == ghAssets.size && mapped.all { it.binary != null || it.checksumMismatch }) {
            "Incomplete binaries for $releaseName"
        }
        val binaries = mapped.mapNotNull { it.binary }
        LOGGER.debug("Done Getting binaries $releaseName")

        return Release(
            id,
            release_type,
            releaseLink,
            releaseName,
            DateTime(timestamp),
            DateTime(updatedAt),
            binaries.toTypedArray(),
            downloadCount(ghAssets) + ungroupedDownloads,
            vendor,
            version,
            sourcePackage,
            releaseNotes,
            aqavitResultsLink
        )
    }

    private fun downloadCount(assets: List<GHAsset>): Long = assets
        .filter { asset -> BinaryMapper.BINARY_EXTENSIONS.any { asset.name.endsWith(it) } }
        .sumOf { it.downloadCount }

    private fun formReleaseType(release: GHRelease): ReleaseType {
        // TODO fix me before the year 2100
        val dateMatcher =
            """.*(20[0-9]{2}-[0-9]{2}-[0-9]{2}|20[0-9]{6}).*"""
        val hasDate = Pattern.compile(dateMatcher).matcher(release.name)

        return if (release.url.matches(Regex(".*/(temurin|openjdk|semeru)[0-9]+-binaries/.*"))) {
            // Can trust isPrerelease from -binaries repos
            if (release.isPrerelease) {
                ReleaseType.ea
            } else {
                ReleaseType.ga
            }
        } else {
            if (hasDate.matches()) {
                ReleaseType.ea
            } else {
                ReleaseType.ga
            }
        }
    }

    private suspend fun associateMetadataWithBinaries(releaseAssets: GHAssets, supportedAssets: List<GHAsset>): Map<GHAsset, GHMetaData> {
        val assetsWithMetadata = releaseAssets
            .assets
            .filter { (it.name.endsWith(".json") && !it.name.contains("sbom")) || (it.name.contains("sbom") && it.name.endsWith("-metadata.json")) }
            .mapNotNull { metadataAsset ->
                pairUpBinaryAndMetadata(releaseAssets, metadataAsset)
            }
            .toMap()

        // Legacy releases (including Semeru) may have no metadata at all. Once
        // metadata is used for grouping, every supported release asset must be accounted
        // for, even when its sidecar node is temporarily absent from GitHub.
        if (assetsWithMetadata.isNotEmpty()) {
            val missingMetadata = supportedAssets.filter { asset ->
                !asset.name.contains("-sources") && asset !in assetsWithMetadata
            }
            check(missingMetadata.isEmpty()) {
                "Required metadata missing for: ${missingMetadata.joinToString { it.name }}"
            }
        }
        return assetsWithMetadata
    }

    private suspend fun pairUpBinaryAndMetadata(releaseAssets: GHAssets, metadataAsset: GHAsset): Pair<GHAsset, GHMetaData>? {
        // Similar names must not pair, e.g. build 10's sidecar with build 1's SBOM.
        val binaryAsset = releaseAssets
            .assets
            .filter(::isReleaseAsset)
            .firstOrNull { metadataAsset.name == metadataNameFor(it) } ?: return null

        // Ignored assets must not make a supported-binary snapshot fail. Source
        // metadata is still needed to identify the release's source package.
        if (!adoptBinaryMapper.isSupportedBinaryAsset(binaryAsset) && !binaryAsset.name.contains("-sources")) {
            return null
        }

        val metadataString = htmlClient.getAsset(metadataAsset)
            ?: throw IllegalStateException("Required metadata unavailable: ${metadataAsset.downloadUrl}")
        return withContext(Dispatchers.IO) {
            val metadata = UpdaterJsonMapper.mapper.readValue(metadataString, GHMetaData::class.java)
            Pair(binaryAsset, metadata)
        }
    }

    private fun metadataNameFor(binary: GHAsset) = if (binary.name.endsWith(".json")) {
        // SBOMs replace their .json extension: sbom.json with sbom-metadata.json
        binary.name.removeSuffix(".json") + "-metadata.json"
    } else {
        binary.name + ".json"
    }

    private fun isReleaseAsset(asset: GHAsset) = (
        !asset.name.endsWith(".json")
            || asset.name.contains("sbom") && !asset.name.endsWith("-metadata.json")
        )

    private fun getSourcePackage(release: GHRelease, ghAssetsWithMetadata: Map<GHAsset, GHMetaData>): SourcePackage? {
        val sources = ghAssetsWithMetadata
            .entries
            .firstOrNull { it.value.binary_type == ImageType.sources }

        if (sources != null) {
            return SourcePackage(sources.key.name, sources.key.downloadUrl, sources.key.size)
        }

        return release.releaseAssets
            .assets
            .filter { it.name.endsWith("tar.gz") }
            .filter { it.name.contains("-sources") }
            .map { SourcePackage(it.name, it.downloadUrl, it.size) }
            .firstOrNull()
    }


    private fun getReleaseNotesPackage(releaseAssets: List<GHAsset>): ReleaseNotesPackage? {
        return releaseAssets
            .filter { it.name.contains("release-notes") }
            .map { ReleaseNotesPackage(it.name, it.downloadUrl, it.size) }
            .firstOrNull()
    }
}
