import com.fasterxml.jackson.module.kotlin.readValue
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Release
import net.adoptium.marketplace.client.MarketplaceMapper
import net.adoptium.marketplace.schema.*
import org.eclipse.jetty.client.HttpClient
import org.junit.jupiter.api.Test
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class ExtractAdoptiumReleases {

    //@Disabled("For manual execution")
    @Test
    fun buildRepo() {
        val httpClient = HttpClient()
        httpClient.isFollowRedirects = true
        httpClient.start()

        val dir = Files.createTempDirectory("repo");

        val indexFile = IndexFile(
            IndexFile.LATEST_VERSION,
            listOf(8, 11, 17)
                .map { "$it/index.json" },
            emptyList()
        )

        val indexfw = FileWriter(Paths.get(dir.toFile().absolutePath, "index.json").toFile())
        indexfw.use {
            it.write(MarketplaceMapper.repositoryObjectMapper.writeValueAsString(indexFile))
        }

        listOf(8, 11, 17)
            .map { version ->

                val versionDir = Path.of(dir.toFile().absolutePath, "$version").toFile();

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
                    IndexFile.LATEST_VERSION,
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

        SignTestAssets.sign(dir.toFile().absolutePath)
    }

    private fun toFileName(it: net.adoptium.marketplace.schema.Release) = it
        .releaseName
        .replace("+", "_")
        .replace(".", "_")
        .replace("-", "_")
        .plus(".json")

    private fun toMarketplaceRelease(release: Release, binaries: List<Binary>): net.adoptium.marketplace.schema.Release {
        return Release(
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

            val upstreamScmRef = binary.scm_ref?.replace("_adopt", "")

            val aqaLink = binary.`package`.link
                .replace(".zip", ".tap.zip")
                .replace(".tar.gz", ".tap.zip")

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
                    listOf(Installer(
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
                upstreamScmRef,
                Distribution.temurin,
                aqaLink,
                "https://adoptium.net/tck_affidavit.html"
            )
        }
        .toList()
}
