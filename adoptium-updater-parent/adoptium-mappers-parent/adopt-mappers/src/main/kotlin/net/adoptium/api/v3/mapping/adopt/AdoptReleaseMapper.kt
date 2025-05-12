package net.adoptium.api.v3.mapping.adopt

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
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
    private val mappers: MutableMap<Vendor, AdoptReleaseMapper> = EnumMap(Vendor::class.java)

    open fun get(vendor: Vendor): ReleaseMapper {
        return if (mappers.containsKey(vendor)) {
            mappers[vendor]!!
        } else {
            val mapper = AdoptReleaseMapper(adoptBinaryMapper, htmlClient, vendor)
            mappers[vendor] = mapper
            mapper
        }
    }
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

    private val excludedReleases: MutableSet<GitHubId> = mutableSetOf()

    override suspend fun toAdoptRelease(ghRelease: GHRelease): ReleaseResult {
        if (excludedReleases.contains(ghRelease.id)) {
            return ReleaseResult(error = "Excluded")
        }

        val releaseType: ReleaseType = formReleaseType(ghRelease)

        val releaseLink = ghRelease.url
        val releaseName = ghRelease.name
        val timestamp = parseDate(ghRelease.publishedAt)
        val updatedAt = parseDate(ghRelease.updatedAt)

        val ghAssetsWithMetadata = associateMetadataWithBinaries(ghRelease.releaseAssets)
        val sourcePackage = getSourcePackage(ghRelease, ghAssetsWithMetadata)
        val releaseNotes = getReleaseNotesPackage(ghRelease.releaseAssets.assets)
        val aqavitResultsLink = getAqavitLink(ghRelease.releaseAssets.assets)

        try {
            val ghAssetsGroupedByVersion = ghAssetsWithMetadata
                .entries
                .groupBy(this@AdoptReleaseMapper::getReleaseVersion)

            val releases = ghAssetsGroupedByVersion
                .entries
                .map { ghAssetsForVersion: Map.Entry<String, List<Map.Entry<GHAsset, GHMetaData>>> ->
                    val version = ghAssetsForVersion.value
                        .sortedBy { ghAssetWithMetadata -> ghAssetWithMetadata.value.version.toApiVersion() }
                        .last().value.version.toApiVersion()

                    val ghAssets: List<GHAsset> = ghAssetsForVersion.value.map { ghAssetWithMetadata -> ghAssetWithMetadata.key }
                    val id = generateIdForSplitRelease(version, ghRelease)

                    toRelease(releaseName, ghAssets, ghAssetsWithMetadata, id, releaseType, releaseLink, timestamp, updatedAt, vendor, version, ghRelease.releaseAssets.assets, sourcePackage, releaseNotes, aqavitResultsLink)
                }
                .ifEmpty {
                    try {
                        // if we have no metadata resort to parsing release names
                        val version = parseVersionInfo(ghRelease, releaseName)
                        val ghAssets = ghRelease.releaseAssets.assets
                        val id = ghRelease.id.id

                        return@ifEmpty listOf(toRelease(releaseName, ghAssets, ghAssetsWithMetadata, id, releaseType, releaseLink, timestamp, updatedAt, vendor, version, ghAssets, sourcePackage, releaseNotes, aqavitResultsLink))
                    } catch (e: Exception) {
                        throw FailedToParse("Failed to parse version $releaseName", e)
                    }
                }
                .filter { updatedRelease -> !excludeRelease(ghRelease, updatedRelease) }

            return ReleaseResult(result = releases)
        } catch (e: FailedToParse) {
            excludedReleases.add(ghRelease.id)
            LOGGER.error("Failed to parse $releaseName")
            return ReleaseResult(error = "Failed to parse $releaseName")
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

    private fun excludeRelease(ghRelease: GHRelease, release: Release): Boolean {
        if (excludedReleases.contains(ghRelease.id)) return true

        return if (release.release_type == ReleaseType.ea) {
            // remove all 14.0.1+7.1 and 15.0.0+24.1 nightlies - https://github.com/AdoptOpenJDK/openjdk-api-v3/issues/213
            // also ignore jdk-2021-01-13-07-01
            if (release.version_data.semver.startsWith("14.0.1+7.1.") ||
                release.version_data.semver.startsWith("15.0.0+24.1.") ||
                release.release_name == "jdk-2021-01-13-07-01" ||
                release.release_name == "jdk17u-2022-05-27-19-32-beta"
            ) {
                // Found an excluded release, mark it for future reference
                excludedReleases.add(ghRelease.id)
                true
            } else {
                false
            }
        } else {
            false
        }
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
        aqavitResultsLink: String?
    ): Release {
        LOGGER.debug("Getting binaries $releaseName")
        val binaries = adoptBinaryMapper.toBinaryList(ghAssets, fullGhAssetList, ghAssetWithMetadata)
        LOGGER.debug("Done Getting binaries $releaseName")

        val downloadCount = ghAssets
            .filter { asset ->
                BinaryMapper.BINARY_EXTENSIONS.any { asset.name.endsWith(it) }
            }
            .sumOf { it.downloadCount }

        return Release(
            id,
            release_type,
            releaseLink,
            releaseName,
            DateTime(timestamp),
            DateTime(updatedAt),
            binaries.toTypedArray(),
            downloadCount,
            vendor,
            version,
            sourcePackage,
            releaseNotes,
            aqavitResultsLink
        )
    }

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

    private fun parseVersionInfo(release: GHRelease, release_name: String): VersionData {
        return try {
            VersionParser.parse(release_name)
        } catch (e: FailedToParse) {
            try {
                getFeatureVersion(release)
            } catch (e: Exception) {
                LOGGER.warn("Failed to parse ${release.name}")
                throw e
            }
        }
    }

    private suspend fun associateMetadataWithBinaries(releaseAssets: GHAssets): Map<GHAsset, GHMetaData> {
        return releaseAssets
            .assets
            .filter { (it.name.endsWith(".json") && !it.name.contains("sbom")) || (it.name.contains("sbom") && it.name.endsWith("-metadata.json")) }
            .mapNotNull { metadataAsset ->
                pairUpBinaryAndMetadata(releaseAssets, metadataAsset)
            }
            .toMap()
    }

    private suspend fun pairUpBinaryAndMetadata(releaseAssets: GHAssets, metadataAsset: GHAsset): Pair<GHAsset, GHMetaData>? {
        val binaryAsset = releaseAssets
            .assets
            .filter(::isReleaseAsset)
            .firstOrNull { // remove .json for matching, case: sbom.json with sbom-metadata.json 
                metadataAsset.name.startsWith(it.name.removeSuffix(".json"))
            }

        val metadataString = htmlClient.getUrl(metadataAsset.downloadUrl)
        if (binaryAsset != null && metadataString != null) {
            try {
                return withContext(Dispatchers.IO) {
                    val metadata = UpdaterJsonMapper.mapper.readValue(metadataString, GHMetaData::class.java)
                    return@withContext Pair(binaryAsset, metadata)
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to read metadata for asset ${metadataAsset.name} ${metadataAsset.downloadUrl}", e)
            }
        }
        return null
    }

    private fun isReleaseAsset(asset: GHAsset) = (
        !asset.name.endsWith(".json")
            || asset.name.contains("sbom") && !asset.name.endsWith("-metadata.json")
        )

    private fun getFeatureVersion(release: GHRelease): VersionData {
        return VersionParser.parse(release.name)
    }

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
