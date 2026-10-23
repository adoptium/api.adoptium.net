package net.adoptium.api.v3.mapping.adopt

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptium.api.v3.mapping.BinaryMapper
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.Installer
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Package
import net.adoptium.api.v3.models.Project
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

@ApplicationScoped
class AdoptBinaryMapper @Inject constructor(private val gitHubHtmlClient: GitHubHtmlClient) : BinaryMapper() {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(AdoptBinaryMapper::class.java)
        private const val HOTSPOT_JFR = "hotspot-jfr"
        private const val TEMURIN = "temurin"

        private val EXCLUDED = listOf("corretto", "AQAvitTapFiles")
        private val SHA256_DIGEST = Regex("sha256:([0-9a-f]{64})", RegexOption.IGNORE_CASE)
    }

    internal data class BinaryMappingResult(val binary: Binary?, val checksumMismatch: Boolean = false)

    suspend fun toBinaryList(ghBinaryAssets: List<GHAsset>, allGhAssets: List<GHAsset>, ghAssetsWithMetadata: Map<GHAsset, GHMetaData>): List<Binary> {
        return mapBinaries(ghBinaryAssets, allGhAssets, ghAssetsWithMetadata).mapNotNull { it.binary }
    }

    internal suspend fun mapBinaries(ghBinaryAssets: List<GHAsset>, allGhAssets: List<GHAsset>, ghAssetsWithMetadata: Map<GHAsset, GHMetaData>): List<BinaryMappingResult> {
        return ghBinaryAssets
            .filter(this::isSupportedBinaryAsset)
            .map { asset -> assetToBinaryAsync(asset, ghAssetsWithMetadata, allGhAssets) }
            .map { it.await() }
    }

    private class ChecksumMismatchException(message: String) : IllegalStateException(message)

    private fun assetToBinaryAsync(
        ghBinaryAsset: GHAsset,
        ghAssetsWithMetadata: Map<GHAsset, GHMetaData>,
        allGhAssets: List<GHAsset>
    ): Deferred<BinaryMappingResult> {
        return GlobalScope.async {
            try {
                val updatedAt = getUpdatedTime(ghBinaryAsset)

                val binaryMetadata = ghAssetsWithMetadata[ghBinaryAsset]

                val heapSize = getEnumFromFileName(ghBinaryAsset.name, HeapSize.entries.toTypedArray(), HeapSize.normal)

                val cLib = getEnumFromFileNameNullable(ghBinaryAsset.name, CLib.entries.toTypedArray(), null)

                val `package` = getPackage(allGhAssets, ghBinaryAsset, binaryMetadata)
                val installer = getInstaller(ghBinaryAsset, allGhAssets, ghAssetsWithMetadata)
                val downloadCount = `package`.download_count + (installer?.download_count ?: 0)

                val binary = if (binaryMetadata != null) {
                    binaryFromMetadata(
                        ghBinaryAsset,
                        binaryMetadata,
                        `package`,
                        downloadCount,
                        updatedAt,
                        installer,
                        heapSize,
                        cLib
                    )
                } else {
                    binaryFromName(ghBinaryAsset, `package`, downloadCount, updatedAt, installer, heapSize, cLib)
                }
                BinaryMappingResult(binary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChecksumMismatchException) {
                LOGGER.error("Excluding binary ${ghBinaryAsset.name}: ${e.message}")
                BinaryMappingResult(null, checksumMismatch = true)
            } catch (e: Exception) {
                LOGGER.error("Failed to fetch binary ${ghBinaryAsset.name}", e)
                BinaryMappingResult(null)
            }
        }
    }

    private suspend fun getPackage(fullAssetList: List<GHAsset>, binaryAsset: GHAsset, binaryMetadata: GHMetaData?): Package {
        val binaryName = binaryAsset.name
        val binaryLink = binaryAsset.downloadUrl
        val binarySize = binaryAsset.size
        val binaryChecksumAsset = getChecksumAsset(fullAssetList, binaryName)
        val signatureLink = getSignatureLink(fullAssetList, binaryAsset.name)

        val binaryChecksum = getVerifiedChecksum(binaryAsset, binaryMetadata?.sha256, binaryChecksumAsset)

        val metadataLink = getMetadataLink(fullAssetList, binaryName)

        return Package(
            binaryName,
            binaryLink,
            binarySize,
            binaryChecksum,
            binaryChecksumAsset?.downloadUrl,
            binaryAsset.downloadCount,
            signature_link = signatureLink,
            metadata_link = metadataLink
        )
    }

    private suspend fun getInstaller(
        binaryAsset: GHAsset,
        fullAssetList: List<GHAsset>,
        ghAssetsWithMetadata: Map<GHAsset, GHMetaData>
    ): Installer? {

        val nameWithoutExtension =
            BINARY_ASSET_WHITELIST.fold(binaryAsset.name) { assetName, extension -> assetName.replace(extension, "") }

        val installer = fullAssetList
            .filter { it.name.startsWith(nameWithoutExtension) }
            .firstOrNull { asset ->
                INSTALLER_EXTENSIONS.any { asset.name.endsWith(it) }
            }

        return if (installer == null) {
            null
        } else {
            val checksumAsset = getChecksumAsset(fullAssetList, installer.name)
            val metadataAsset = getMetadataAsset(fullAssetList, installer.name)
            val checksum = try {
                val metadata = ghAssetsWithMetadata[installer] ?: metadataAsset?.let { asset ->
                    val content = checkNotNull(gitHubHtmlClient.getAsset(asset)) {
                        "Installer metadata unavailable: ${asset.downloadUrl}"
                    }
                    UpdaterJsonMapper.mapper.readValue(content, GHMetaData::class.java)
                }
                getVerifiedChecksum(installer, metadata?.sha256, checksumAsset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChecksumMismatchException) {
                LOGGER.error("Excluding installer ${installer.name}: ${e.message}")
                return null
            } catch (e: Exception) {
                LOGGER.error("Excluding installer ${installer.name} after metadata/checksum validation failed", e)
                return null
            }

            val signatureLink = getSignatureLink(fullAssetList, installer.name)

            Installer(
                installer.name,
                installer.downloadUrl,
                installer.size,
                checksum,
                checksumAsset?.downloadUrl,
                installer.downloadCount,
                signature_link = signatureLink,
                metadata_link = metadataAsset?.downloadUrl
            )
        }
    }

    private fun getDigestChecksum(asset: GHAsset): String? {
        return SHA256_DIGEST.matchEntire(asset.digest.orEmpty())?.groupValues?.get(1)?.lowercase()
    }

    private suspend fun getVerifiedChecksum(asset: GHAsset, metadataChecksum: String?, checksumAsset: GHAsset?): String? {
        val githubChecksum = getDigestChecksum(asset)
        val metadata = metadataChecksum?.takeIf { it.isNotBlank() }
        val sidecar = if (githubChecksum != null || metadata == null) getChecksum(checksumAsset) else null
        if (githubChecksum != null) {
            for ((source, checksum) in listOf("metadata" to metadata, "checksum sidecar" to sidecar)) {
                if (checksum != null && !githubChecksum.equals(checksum.trim(), ignoreCase = true)) {
                    throw ChecksumMismatchException(
                        "SHA-256 mismatch for ${asset.downloadUrl}: GitHub=$githubChecksum, $source=$checksum"
                    )
                }
            }
        }
        return githubChecksum ?: metadata ?: sidecar
    }

    private fun getChecksumAsset(fullAssetList: List<GHAsset>, binary_name: String): GHAsset? {
        val nameWithoutExtension = removeExtensionFromName(binary_name)

        return fullAssetList
            .firstOrNull { asset ->
                asset.name == "$binary_name.sha256.txt" ||
                    asset.name == binary_name.split(".")[0] + ".sha256.txt" ||
                    asset.name == "$nameWithoutExtension.sha256.txt"
            }
    }

    private fun getMetadataLink(fullAssetList: List<GHAsset>, binary_name: String): String? {
        return getMetadataAsset(fullAssetList, binary_name)?.downloadUrl
    }

    private fun getMetadataAsset(fullAssetList: List<GHAsset>, binary_name: String): GHAsset? {
        val nameWithoutExtension = removeExtensionFromName(binary_name)

        return fullAssetList.firstOrNull { it.name == "$binary_name.json" }
            ?: fullAssetList
            .firstOrNull { asset ->
                asset.name == binary_name.split(".")[0] + ".json" ||
                    asset.name == "$nameWithoutExtension.json"
            }
    }

    private fun removeExtensionFromName(binary_name: String): String {
        return BINARY_ASSET_WHITELIST.foldRight(binary_name) { extension, name -> name.removeSuffix(extension) }
    }

    internal fun isSupportedBinaryAsset(asset: GHAsset) =
        (ARCHIVE_WHITELIST.any { asset.name.endsWith(it) } || (asset.name.endsWith(".json") && asset.name.contains("-sbom_"))) &&
            EXCLUDED.none { asset.name.contains(it) }

    private fun binaryFromName(
        asset: GHAsset,
        pack: Package,
        download_count: Long,
        updated_at: ZonedDateTime,
        installer: Installer?,
        heap_size: HeapSize,
        cLib: CLib?
    ): Binary {
        val scmRef = null
        val os = getEnumFromFileName(asset.name, OperatingSystem.entries.toTypedArray())
        val architecture = getEnumFromFileName(asset.name, Architecture.entries.toTypedArray())
        val binaryType = getEnumFromFileName(asset.name, ImageType.entries.toTypedArray(), ImageType.jdk)
        val jvmImpl = getEnumFromFileName(asset.name, JvmImpl.entries.toTypedArray(), JvmImpl.hotspot)
        val project = getEnumFromFileName(asset.name, Project.entries.toTypedArray(), Project.jdk)

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
            project,
            cLib,
        )
    }

    private fun binaryFromMetadata(
        asset: GHAsset,
        binaryMetadata: GHMetaData,
        pack: Package,
        download_count: Long,
        updated_at: ZonedDateTime,
        installer: Installer?,
        heap_size: HeapSize,
        cLib: CLib?
    ): Binary {

        // github metadata has concept of hotspot-jfr split this into
        val variant = parseJvmImpl(binaryMetadata)
        val project = parseProject(binaryMetadata)
        val imageType = getImageType(asset, binaryMetadata)

        return Binary(
            pack,
            download_count,
            DateTime(updated_at),
            binaryMetadata.scmRef,
            installer,
            heap_size,
            binaryMetadata.os,
            binaryMetadata.arch,
            imageType,
            variant,
            project,
            cLib,
        )
    }

    private fun getImageType(asset: GHAsset, binaryMetadata: GHMetaData): ImageType {
        // static-libs incorrectly labeled as JDK, if the name tells us it's a static-lib, trust that over the metadata file
        val binaryTypeFromName = getEnumFromFileName(asset.name, ImageType.entries.toTypedArray(), ImageType.jdk)
        return if (binaryTypeFromName == ImageType.staticlibs) {
            ImageType.staticlibs
        } else {
            binaryMetadata.binary_type
        }
    }

    private fun parseProject(binaryMetadata: GHMetaData): Project {
        return if (binaryMetadata.variant == HOTSPOT_JFR) {
            Project.jfr
        } else {
            Project.jdk
        }
    }

    private fun parseJvmImpl(binaryMetadata: GHMetaData): JvmImpl {
        return if (
            binaryMetadata.variant == HOTSPOT_JFR ||
            binaryMetadata.variant == TEMURIN
        ) {
            JvmImpl.hotspot
        } else {
            JvmImpl.valueOf(binaryMetadata.variant)
        }
    }

    private suspend fun getChecksum(checksumAsset: GHAsset?): String? {
        try {
            if (checksumAsset != null && checksumAsset.downloadUrl.isNotEmpty()) {
                LOGGER.debug("Pulling checksum for ${checksumAsset.downloadUrl}")
                val checksum = gitHubHtmlClient.getAsset(checksumAsset)
                if (checksum != null) {
                    return checksum.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.first()
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to fetch checksum ${checksumAsset?.downloadUrl}", e)
        }
        return null
    }

    private fun getSignatureLink(assets: List<GHAsset>, binary_name: String): String? {
        return assets
            .firstOrNull { asset ->
                asset.name == "$binary_name.sig"
            }?.downloadUrl
    }
}
