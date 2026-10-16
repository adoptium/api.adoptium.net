package net.adoptium.api.v3.mapping.adopt

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
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
    }


    suspend fun toBinaryList(ghBinaryAssets: List<GHAsset>, allGhAssets: List<GHAsset>, ghAssetsWithMetadata: Map<GHAsset, GHMetaData>): List<Binary> {
        return toSelectedBinaryList(ghBinaryAssets.filter(::isSupportedBinaryAsset), allGhAssets, ghAssetsWithMetadata)
    }

    internal suspend fun toSelectedBinaryList(ghBinaryAssets: List<GHAsset>, allGhAssets: List<GHAsset>, ghAssetsWithMetadata: Map<GHAsset, GHMetaData>): List<Binary> {
        return ghBinaryAssets
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

                val heapSize = getEnumFromFileName(ghBinaryAsset.name, HeapSize.entries.toTypedArray(), HeapSize.normal)

                val cLib = getEnumFromFileNameNullable(ghBinaryAsset.name, CLib.entries.toTypedArray(), null)

                val installer = getInstaller(ghBinaryAsset, allGhAssets)
                val `package` = getPackage(allGhAssets, ghBinaryAsset, binaryMetadata)
                val downloadCount = `package`.download_count + (installer?.download_count ?: 0)

                if (binaryMetadata != null) {
                    return@async binaryFromMetadata(
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
                    return@async binaryFromName(ghBinaryAsset, `package`, downloadCount, updatedAt, installer, heapSize, cLib)
                }
            } catch (e: CancellationException) {
                throw e
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
        val checksumAsset = getChecksumAsset(fullAssetList, binaryName)
        val binaryChecksumLink = checksumAsset?.downloadUrl
        val signatureLink = getSignatureLink(fullAssetList, binaryAsset.name)

        val binaryChecksum: String? = binaryAsset.sha256() ?:
            if (binaryMetadata != null && binaryMetadata.sha256.isNotEmpty()) {
                binaryMetadata.sha256
            } else {
                getChecksum(checksumAsset)
            }

        val metadataLink = getMetadataLink(fullAssetList, binaryName)

        return Package(
            binaryName,
            binaryLink,
            binarySize,
            binaryChecksum,
            binaryChecksumLink,
            binaryAsset.downloadCount,
            signature_link = signatureLink,
            metadata_link = metadataLink
        )
    }

    private suspend fun getInstaller(binaryAsset: GHAsset, fullAssetList: List<GHAsset>): Installer? {

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
            val checkSumLink = checksumAsset?.downloadUrl
            val checksum = installer.sha256() ?: getChecksum(checksumAsset)

            val metadataLink = getMetadataLink(fullAssetList, installer.name)
            val signatureLink = getSignatureLink(fullAssetList, installer.name)

            Installer(
                installer.name,
                installer.downloadUrl,
                installer.size,
                checksum,
                checkSumLink,
                installer.downloadCount,
                signature_link = signatureLink,
                metadata_link = metadataLink
            )
        }
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
        val nameWithoutExtension = removeExtensionFromName(binary_name)

        return fullAssetList
            .firstOrNull { asset ->
                asset.name == "$binary_name.json" ||
                    asset.name == binary_name.split(".")[0] + ".json" ||
                    asset.name == "$nameWithoutExtension.json"
            }?.downloadUrl
    }

    private fun removeExtensionFromName(binary_name: String): String {
        return BINARY_ASSET_WHITELIST.foldRight(binary_name) { extension, name -> name.removeSuffix(extension) }
    }

    internal fun isSupportedBinaryAsset(asset: GHAsset) =
        (ARCHIVE_WHITELIST.any { asset.name.endsWith(it) } ||
            (asset.name.endsWith(".json") && asset.name.contains("-sbom_") && !asset.name.endsWith("-metadata.json"))) &&
            !asset.name.contains("-sources") && EXCLUDED.none { asset.name.contains(it) }

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

    private suspend fun getChecksum(asset: GHAsset?): String? {
        if (asset == null) return null
        val checksum = checkNotNull(gitHubHtmlClient.getAsset(asset)) { "Checksum unavailable: ${asset.name}" }
        return checksum.trim().split(Regex("\\s+")).first().also {
            check(it.isNotEmpty()) { "Empty checksum: ${asset.name}" }
        }
    }

    private fun getSignatureLink(assets: List<GHAsset>, binary_name: String): String? {
        return assets
            .firstOrNull { asset ->
                asset.name == "$binary_name.sig"
            }?.downloadUrl
    }
}
