package net.adoptopenjdk.api.v3.mapping.adopt

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptopenjdk.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptopenjdk.api.v3.mapping.BinaryMapper
import net.adoptopenjdk.api.v3.models.*
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemeruBinaryMapper @Inject constructor(private val gitHubHtmlClient: GitHubHtmlClient) : BinaryMapper() {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        private const val HOTSPOT_JFR = "hotspot-jfr"
    }

    suspend fun toBinaryList(ghBinaryAssets: List<GHAsset>, allGhAssets: List<GHAsset>, ghAssetsWithMetadata: Map<GHAsset, GHMetaData>): List<Binary> {
        // probably whitelist rather than black list
        return ghBinaryAssets
            .filter(this::isArchive)
            .map { asset -> assetToBinaryAsync(asset, ghAssetsWithMetadata, allGhAssets) }
            .mapNotNull { it.await() }
    }

    private fun assetToBinaryAsync(
        ghBinaryAsset: GHAsset,
        ghAssetsWithMetadata: Map<GHAsset, GHMetaData>,
        allGhAssets: List<GHAsset>
    ): Deferred<Binary?> {
        return GlobalScope.async {
            try {
                val updatedAt = getUpdatedTime(ghBinaryAsset)

                val binaryMetadata = ghAssetsWithMetadata[ghBinaryAsset]

                val heapSize = getEnumFromFileName(ghBinaryAsset.name, HeapSize.values(), HeapSize.normal)

                val installer = getInstaller(ghBinaryAsset, allGhAssets)
                val `package` = getPackage(allGhAssets, ghBinaryAsset, binaryMetadata)
                val downloadCount = `package`.download_count + (installer?.download_count ?: 0)

                if (binaryMetadata != null) {
                    return@async binaryFromMetadata(
                        binaryMetadata,
                        `package`,
                        downloadCount,
                        updatedAt,
                        installer,
                        heapSize
                    )
                } else {
                    return@async binaryFromName(ghBinaryAsset, `package`, downloadCount, updatedAt, installer, heapSize)
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to fetch binary ${ghBinaryAsset.name}", e)
                return@async null
            }
        }
    }

    private suspend fun getPackage(fullAssetList: List<GHAsset>, binaryAsset: GHAsset, binaryMetadata: GHMetaData?): Package {
        val binaryName = binaryAsset.name
        val binaryLink = binaryAsset.downloadUrl
        val binarySize = binaryAsset.size
        val binaryChecksumLink = getCheckSumLink(fullAssetList, binaryName)
        val binaryChecksum: String?

        binaryChecksum = if (binaryMetadata != null && binaryMetadata.sha256.isNotEmpty()) {
            binaryMetadata.sha256
        } else {
            getChecksum(binaryChecksumLink)
        }

        val metadataLink = getMetadataLink(fullAssetList, binaryName)

        return Package(
            binaryName,
            binaryLink,
            binarySize,
            binaryChecksum,
            binaryChecksumLink,
            binaryAsset.downloadCount,
            signature_link = null,
            metadata_link = metadataLink
        )
    }

    private suspend fun getInstaller(binaryAsset: GHAsset, fullAssetList: List<GHAsset>): Installer? {

        val nameWithoutExtension =
            BINARY_ASSET_WHITELIST.fold(binaryAsset.name, { assetName, extension -> assetName.replace(extension, "") })

        val installer = fullAssetList
            .filter { it.name.startsWith(nameWithoutExtension) }
            .firstOrNull { asset ->
                INSTALLER_EXTENSIONS.any { asset.name.endsWith(it) }
            }

        return if (installer == null) {
            null
        } else {
            val checkSumLink = getCheckSumLink(fullAssetList, installer.name)
            var checksum: String? = null
            if (checkSumLink != null) {
                checksum = getChecksum(checkSumLink)
            }

            val metadataLink = getMetadataLink(fullAssetList, installer.name)

            Installer(
                installer.name,
                installer.downloadUrl,
                installer.size,
                checksum,
                checkSumLink,
                installer.downloadCount,
                signature_link = null,
                metadata_link = metadataLink
            )
        }
    }

    private fun getCheckSumLink(fullAssetList: List<GHAsset>, binary_name: String): String? {
        val nameWithoutExtension = removeExtensionFromName(binary_name)

        return fullAssetList
            .firstOrNull { asset ->
                asset.name == "$binary_name.sha256.txt" ||
                    asset.name == binary_name.split(".")[0] + ".sha256.txt" ||
                    asset.name == "$nameWithoutExtension.sha256.txt"
            }?.downloadUrl
    }

    private fun getMetadataLink(fullAssetList: List<GHAsset>, binary_name: String): String? {
        val nameWithoutExtension = removeExtensionFromName(binary_name)

        return fullAssetList
            .firstOrNull { asset ->
                asset.name == "$binary_name.json" ||
                    asset.name == binary_name.split(".")[0] + ".json" ||
                    asset.name == "$nameWithoutExtension.json"
            }?.downloadUrl
    }

    private fun removeExtensionFromName(binary_name: String): String {
        return BINARY_ASSET_WHITELIST.foldRight(binary_name, { extension, name -> name.removeSuffix(extension) })
    }

    private fun isArchive(asset: GHAsset) = ARCHIVE_WHITELIST.any { asset.name.endsWith(it) }

    private fun binaryFromName(
        asset: GHAsset,
        pack: Package,
        download_count: Long,
        updated_at: ZonedDateTime,
        installer: Installer?,
        heap_size: HeapSize
    ): Binary {
        val scmRef = null
        val os = getEnumFromFileName(asset.name, OperatingSystem.values())
        val architecture = getEnumFromFileName(asset.name, Architecture.values())
        val binaryType = getEnumFromFileName(asset.name, ImageType.values(), ImageType.jdk)
        val jvmImpl = getEnumFromFileName(asset.name, JvmImpl.values(), JvmImpl.hotspot)
        val project = getEnumFromFileName(asset.name, Project.values(), Project.jdk)

        return Binary(
            pack,
            download_count,
            DateTime(updated_at),
            scmRef,
            installer,
            heap_size,
            os,
            architecture,
            binaryType,
            jvmImpl,
            project
        )
    }

    private fun binaryFromMetadata(
        binaryMetadata: GHMetaData,
        pack: Package,
        download_count: Long,
        updated_at: ZonedDateTime,
        installer: Installer?,
        heap_size: HeapSize
    ): Binary {

        // github metadata has concept of hotspot-jfr split this into
        val variant = parseJvmImpl(binaryMetadata)
        val project = parseProject(binaryMetadata)

        return Binary(
            pack,
            download_count,
            DateTime(updated_at),
            binaryMetadata.scmRef,
            installer,
            heap_size,
            binaryMetadata.os,
            binaryMetadata.arch,
            binaryMetadata.binary_type,
            variant,
            project
        )
    }

    private fun parseProject(binaryMetadata: GHMetaData): Project {
        return if (binaryMetadata.variant == HOTSPOT_JFR) {
            Project.jfr
        } else {
            Project.jdk
        }
    }

    private fun parseJvmImpl(binaryMetadata: GHMetaData): JvmImpl {
        return if (binaryMetadata.variant == HOTSPOT_JFR) {
            JvmImpl.hotspot
        } else {
            JvmImpl.valueOf(binaryMetadata.variant)
        }
    }

    private suspend fun getChecksum(binary_checksum_link: String?): String? {
        try {
            if (!(binary_checksum_link == null || binary_checksum_link.isEmpty())) {
                LOGGER.debug("Pulling checksum for $binary_checksum_link")
                val checksum = gitHubHtmlClient.getUrl(binary_checksum_link)
                if (checksum != null) {
                    val tokens = checksum.split(" ")
                    if (tokens.size > 1) {
                        return tokens[0]
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to fetch checksum $binary_checksum_link", e)
        }
        return null
    }
}
