import com.fasterxml.jackson.module.kotlin.readValue
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Release
import net.adoptium.marketplace.client.MarketplaceMapper
import net.adoptium.marketplace.schema.*
import org.eclipse.jetty.client.HttpClient
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class GenerateAdoptiumRepo {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {

            val versions = getVersionsToAddToRepo()
            val outputDir = getOutputDir()

            GenerateAdoptiumRepo().buildRepo(outputDir, versions)
        }

        private fun getOutputDir(): File {
            val file = Path.of(System.getProperty("OUTPUT_DIR", "./repo")).toFile()
            if (!file.exists()) {
                file.mkdirs()
            }
            return file
        }

        private fun getVersionsToAddToRepo(): List<Int> {
            val versionString = System.getProperty("VERSIONS", "8,11,17")

            return versionString
                .split(",")
                .map { Integer.parseInt(it) }
        }
    }

    fun buildRepo(outputDir: File, versions: List<Int>) {
        val httpClient = HttpClient()
        httpClient.isFollowRedirects = true
        httpClient.start()

        val indexFile = IndexFile(
            "1.0.0",
            versions
                .map { "$it/index.json" },
            emptyList()
        )

        val indexfw = FileWriter(Paths.get(outputDir.absolutePath, "index.json").toFile())
        indexfw.use {
            it.write(MarketplaceMapper.repositoryObjectMapper.writeValueAsString(indexFile))
        }

        versions
            .map { version ->

                val versionDir = Path.of(outputDir.absolutePath, "$version").toFile();

                versionDir.mkdirs()

                // Possibly might need to check next page...one day
                val response = httpClient.GET("https://api.adoptium.net/v3/assets/feature_releases/${version}/ga?page_size=50&vendor=eclipse")

                val releases = JsonMapper.mapper.readValue<List<Release>>(response.content)

                val marketplaceReleases = releases
                    .map { release ->
                        ReleaseList(listOf(toMarketplaceRelease(release, toMarketplaceBinaries(release))))
                    }
                    .toList()

                val indexFile = IndexFile(
                    "1.0.0",
                    emptyList(),
                    marketplaceReleases
                        .map { toFileName(it.releases.first()) }
                )

                val indexfw = FileWriter(Paths.get(versionDir.absolutePath, "index.json").toFile())
                indexfw.use {
                    it.write(MarketplaceMapper.repositoryObjectMapper.writeValueAsString(indexFile))
                }

                marketplaceReleases
                    .forEach { release ->
                        val fos = FileWriter(Paths.get(versionDir.absolutePath, toFileName(release.releases.first())).toFile())
                        fos.use {
                            it.write(MarketplaceMapper.repositoryObjectMapper.writeValueAsString(release))
                        }
                    }
            }

        httpClient.stop()

        SignAssets.sign(outputDir.absolutePath)
    }

    private fun toFileName(it: net.adoptium.marketplace.schema.Release) = it
        .releaseName
        .replace("+", "_")
        .replace(".", "_")
        .replace("-", "_")
        .plus(".json")

    private fun toMarketplaceRelease(release: Release, binaries: List<Binary>): net.adoptium.marketplace.schema.Release {
        return net.adoptium.marketplace.schema.Release(
            release.release_link,
            release.release_name,
            Date.from(release.timestamp.dateTime.toInstant()),
            binaries,
            Vendor.adoptium,
            OpenjdkVersionData(
                release.version_data.major,
                release.version_data.minor,
                release.version_data.security,
                release.version_data.patch,
                release.version_data.pre,
                release.version_data.build,
                release.version_data.optional,
                release.version_data.openjdk_version
            ),
            if (release.source != null) {
                SourcePackage(
                    release.source!!.name,
                    release.source!!.link
                )
            } else null,
            null
        )
    }

    private fun toMarketplaceBinaries(release: Release) = release
        .binaries
        .map { binary ->
            val arch = if (binary.os == net.adoptium.api.v3.models.OperatingSystem.`alpine-linux`) {
                OperatingSystem.alpine_linux
            } else {
                OperatingSystem.valueOf(binary.os.name)
            }
            Binary(
                arch,
                Architecture.valueOf(binary.architecture.name),
                ImageType.valueOf(binary.image_type.name),
                if (binary.c_lib != null) CLib.valueOf(binary.c_lib!!.name) else null,
                JvmImpl.valueOf(binary.jvm_impl.name),
                Package(
                    binary.`package`.name,
                    binary.`package`.link,
                    binary.`package`.checksum,
                    binary.`package`.checksum_link,
                    binary.`package`.signature_link
                ),
                if (binary.installer != null) {
                    listOf(
                        Installer(
                            binary.installer!!.name,
                            binary.installer!!.link,
                            binary.installer!!.checksum,
                            binary.installer!!.checksum_link,
                            binary.installer!!.signature_link,
                            null
                        )
                    )
                } else null,
                Date.from(binary.updated_at.dateTime.toInstant()),
                binary.scm_ref,
                "unknown",
                Distribution.temurin,
                "https://adoptium.net/aqavit/${binary.`package`.name}/aqavit_affidavit.html",
                "https://adoptium.net/tck_affidavit.html"
            )
        }
        .toList()
}
