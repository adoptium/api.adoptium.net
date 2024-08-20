package net.adoptium.api.v3.mapping.upstream

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.mapping.BinaryMapper
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Package
import net.adoptium.api.v3.models.Project
import org.slf4j.LoggerFactory

object UpstreamBinaryMapper : BinaryMapper() {

    @JvmStatic
    private val LOGGER = LoggerFactory.getLogger(this::class.java)

    private val EXCLUDES = listOf("sources", "debuginfo")

    suspend fun toBinaryList(assets: List<GHAsset>): List<Binary> {
        return assets
            .filter(this::isArchive)
            .filter { !assetIsExcluded(it) }
            .map { asset -> assetToBinaryAsync(asset, assets) }
            .mapNotNull { it.await() }
    }

    private fun assetIsExcluded(asset: GHAsset) = EXCLUDES.any { exclude -> asset.name.contains(exclude) }

    private fun assetToBinaryAsync(asset: GHAsset, assets: List<GHAsset>): Deferred<Binary?> {
        return GlobalScope.async {
            try {
                val signatureLink = getSignatureLink(assets, asset.name)
                val pack = Package(asset.name, asset.downloadUrl, asset.size, null, null, asset.downloadCount, signatureLink, null)

                val os = getEnumFromFileName(asset.name, OperatingSystem.entries.toTypedArray())
                val architecture = getEnumFromFileName(asset.name, Architecture.entries.toTypedArray())
                val imageType = getEnumFromFileName(asset.name, ImageType.entries.toTypedArray(), ImageType.jdk)
                val updatedAt = getUpdatedTime(asset)
                val projectType = getEnumFromFileName(asset.name, Project.entries.toTypedArray(), Project.jdk)

                Binary(
                    pack,
                    asset.downloadCount,
                    DateTime(updatedAt),
                    null,
                    null,
                    HeapSize.normal,
                    os,
                    architecture,
                    imageType,
                    JvmImpl.hotspot,
                    projectType,
                    null,
                )
            } catch (e: Exception) {
                LOGGER.error("Failed to parse binary data", e)
                return@async null
            }
        }
    }

    private fun isArchive(asset: GHAsset) = ARCHIVE_WHITELIST.any { asset.name.endsWith(it) }

    private fun getSignatureLink(assets: List<GHAsset>, binary_name: String): String? {
        return assets
            .firstOrNull { asset ->
                asset.name == "$binary_name.sign"
            }?.downloadUrl
    }
}
