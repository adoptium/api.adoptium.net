package net.adoptopenjdk.api.v3.mapping.adopt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.adoptopenjdk.api.v3.ReleaseResult
import net.adoptopenjdk.api.v3.dataSources.UpdaterJsonMapper
import net.adoptopenjdk.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptopenjdk.api.v3.mapping.BinaryMapper
import net.adoptopenjdk.api.v3.mapping.ReleaseMapper
import net.adoptopenjdk.api.v3.models.DateTime
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.models.VersionData
import net.adoptopenjdk.api.v3.parser.FailedToParse
import net.adoptopenjdk.api.v3.parser.VersionParser
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.Base64
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemeruReleaseMapperFactory @Inject constructor(
    semeruBinaryMapper: SemeruBinaryMapper,
    val htmlClient: GitHubHtmlClient
) {
    private val mapper = SemeruReleaseMapper(semeruBinaryMapper, htmlClient, Vendor.ibm);

    fun get(): ReleaseMapper {
        return mapper
    }
}

class SemeruReleaseMapper constructor(
    val semeruBinaryMapper: SemeruBinaryMapper,
    val htmlClient: GitHubHtmlClient,
    val vendor: Vendor
) : ReleaseMapper() {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun toAdoptRelease(ghRelease: GHRelease): ReleaseResult {

        val releaseType: ReleaseType = formReleaseType(ghRelease)

        val releaseLink = ghRelease.url
        val releaseName = ghRelease.name
        val timestamp = parseDate(ghRelease.publishedAt)
        val updatedAt = parseDate(ghRelease.updatedAt)

        val ghAssetsWithMetadata = associateMetadataWithBinaries(ghRelease.releaseAssets)

        try {
            val ghAssetsGroupedByVersion = ghAssetsWithMetadata
                .entries
                .groupBy(this::getReleaseVersion)

            val releases = ghAssetsGroupedByVersion
                .entries
                .map { ghAssetsForVersion: Map.Entry<String, List<Map.Entry<GHAsset, GHMetaData>>> ->
                    val version = ghAssetsForVersion.value
                        .sortedBy { ghAssetWithMetadata -> ghAssetWithMetadata.value.version.toApiVersion() }
                        .last().value.version.toApiVersion()

                    val ghAssets: List<GHAsset> = ghAssetsForVersion.value.map { ghAssetWithMetadata -> ghAssetWithMetadata.key }
                    val id = generateIdForSplitRelease(version, ghRelease)

                    toRelease(releaseName, ghAssets, ghAssetsWithMetadata, id, releaseType, releaseLink, timestamp, updatedAt, vendor, version, ghRelease.releaseAssets.assets)
                }
                .ifEmpty {
                    try {
                        // if we have no metadata resort to parsing release names
                        val version = parseVersionInfo(ghRelease, releaseName)
                        val ghAssets = ghRelease.releaseAssets.assets
                        val id = ghRelease.id.id

                        return@ifEmpty listOf(toRelease(releaseName, ghAssets, ghAssetsWithMetadata, id, releaseType, releaseLink, timestamp, updatedAt, vendor, version, ghAssets))
                    } catch (e: Exception) {
                        throw FailedToParse("Failed to parse version $releaseName", e)
                    }
                }

            return ReleaseResult(result = releases)
        } catch (e: FailedToParse) {
            LOGGER.error("Failed to parse $releaseName")
            return ReleaseResult(error = "Failed to parse $releaseName")
        }
    }

    private fun getReleaseVersion(ghAssetWithMetadata: Map.Entry<GHAsset, GHMetaData>): String {
        val version = ghAssetWithMetadata.value.version
        return "${version.major}.${version.minor}.${version.security}.${version.build}.${version.adopt_build_number}.${version.pre}"
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
        fullGhAssetList: List<GHAsset>
    ): Release {
        LOGGER.debug("Getting binaries $releaseName")
        val binaries = semeruBinaryMapper.toBinaryList(ghAssets, fullGhAssetList, ghAssetWithMetadata)
        LOGGER.debug("Done Getting binaries $releaseName")

        val downloadCount = ghAssets
            .filter { asset ->
                BinaryMapper.BINARY_EXTENSIONS.any { asset.name.endsWith(it) }
            }
            .map { it.downloadCount }.sum()

        return Release(id, release_type, releaseLink, releaseName, DateTime(timestamp), DateTime(updatedAt), binaries.toTypedArray(), downloadCount, vendor, version)
    }

    private fun formReleaseType(release: GHRelease): ReleaseType {
        // TODO fix me before the year 2100
        val dateMatcher =
            """.*(20[0-9]{2}-[0-9]{2}-[0-9]{2}|20[0-9]{6}).*"""
        val hasDate = Pattern.compile(dateMatcher).matcher(release.name)

        return if (release.url.matches(Regex(".*/openjdk[0-9]+-binaries/.*"))) {
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
            .filter { it.name.endsWith(".json") }
            .mapNotNull { metadataAsset ->
                pairUpBinaryAndMetadata(releaseAssets, metadataAsset)
            }
            .toMap()
    }

    private suspend fun pairUpBinaryAndMetadata(releaseAssets: GHAssets, metadataAsset: GHAsset): Pair<GHAsset, GHMetaData>? {
        val binaryAsset = releaseAssets
            .assets
            .firstOrNull {
                metadataAsset.name.startsWith(it.name)
            }

        val metadataString = htmlClient.getUrl(metadataAsset.downloadUrl)
        if (binaryAsset != null && metadataString != null) {
            try {
                return withContext(Dispatchers.IO) {
                    val metadata = UpdaterJsonMapper.mapper.readValue(metadataString, GHMetaData::class.java)
                    return@withContext Pair(binaryAsset, metadata)
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to read metadata", e)
            }
        }
        return null
    }

    private fun getFeatureVersion(release: GHRelease): VersionData {
        return VersionParser.parse(release.name)
    }
}
