import com.fasterxml.jackson.module.kotlin.readValue
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Release
import net.adoptium.marketplace.client.MarketplaceMapper
import net.adoptium.marketplace.schema.*
import org.eclipse.jetty.client.HttpClient
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class ExtractAdoptiumReleases {

    companion object {
        val VERSIONS = listOf(8, 11, 17)
    }

    //@Disabled("For manual execution")
    @Test
    fun buildRepo() {
        val httpClient = HttpClient()
        httpClient.isFollowRedirects = true
        httpClient.start()

        val dir = File("/tmp/adoptiumRepo").toPath()
        dir.toFile().mkdirs()

        // Write ./index.json file
        createTopLevelIndexFile(dir)

        // Only doing LTS for now
        VERSIONS
            .map { version ->
                // Get directory to write to, i.e `./8/`
                val versionDir = Path.of(dir.toFile().absolutePath, "$version").toFile()
                versionDir.mkdirs()

                // Fetch releases in Adoptiums (NOT marketplace) schema
                val releases = getAdoptiumReleases(httpClient, version)

                // Represent Adoptium releases in Marketplace schema
                val marketplaceReleases = convertToMarketplaceSchema(releases)

                // Create index file i.e './8/index.json
                val indexFile = IndexFile(
                    IndexFile.LATEST_VERSION,
                    emptyList(),
                    marketplaceReleases
                        .map { toFileName(it.releases.first()) }
                )
                val file = Paths.get(versionDir.absolutePath, "index.json").toFile();
                if (file.exists()) {
                    throw RuntimeException("File Name Clash " + file.absolutePath)
                }
                val indexfw = FileWriter(file)
                indexfw.use {
                    it.write(MarketplaceMapper.repositoryObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indexFile))
                }

                // Write all releases to file
                marketplaceReleases
                    .forEach { release ->
                        // write to file, i.e ./8/jdk8u302_b08.json
                        val fos = FileWriter(Paths.get(versionDir.absolutePath, toFileName(release.releases.first())).toFile())

                        // Serialize object to file
                        fos.use {
                            it.write(MarketplaceMapper.repositoryObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(release))
                        }
                    }
            }

        httpClient.stop()

        print("Created repo $dir")

        // Sign new files
        SignTestAssets.sign(dir.toFile().absolutePath)
    }

    private fun createTopLevelIndexFile(dir: Path) {
        /* Write top level index file, produces:
            {
              "indexes": [
                "8/index.json",
                "11/index.json",
                "17/index.json"
              ],
              "releases": []
            }
         */
        val indexFile = IndexFile(
            IndexFile.LATEST_VERSION,
            VERSIONS
                .map { "$it/index.json" },
            emptyList()
        )

        val indexfw = FileWriter(Paths.get(dir.toFile().absolutePath, "index.json").toFile())
        indexfw.use {
            it.write(MarketplaceMapper.repositoryObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indexFile))
        }
    }

    private fun convertToMarketplaceSchema(releases: List<Release>): List<ReleaseList> {
        val marketplaceReleases = releases
            .map { release ->
                ReleaseList(listOf(toMarketplaceRelease(release, toMarketplaceBinaries(release))))
            }
            .toList()
        return marketplaceReleases
    }

    private fun getAdoptiumReleases(httpClient: HttpClient, version: Int): List<Release> {
        // Possibly might need to check next page...one day
        val response = httpClient.GET("https://api.adoptium.net/v3/assets/feature_releases/${version}/ga?page_size=50&vendor=eclipse")

        val releases = JsonMapper.mapper.readValue<List<Release>>(response.content)

        return releases
            .map { release ->
                val filteredBinaries = release.binaries.filter {
                    it.image_type == net.adoptium.api.v3.models.ImageType.jdk ||
                        it.image_type == net.adoptium.api.v3.models.ImageType.jre
                }

                Release(release, filteredBinaries.toTypedArray())
            }
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
            val os = if (binary.os == net.adoptium.api.v3.models.OperatingSystem.`alpine-linux`) {
                OperatingSystem.alpine_linux
            } else {
                OperatingSystem.valueOf(binary.os.name)
            }

            val arch = if (binary.architecture == net.adoptium.api.v3.models.Architecture.x32) {
                Architecture.x86
            } else {
                Architecture.valueOf(binary.architecture.name)
            }

            val upstreamScmRef = binary.scm_ref?.replace("_adopt", "")

            val aqaLink = binary.`package`.link
                .replace(".zip", ".tap.zip")
                .replace(".tar.gz", ".tap.zip")

            Binary(
                os,
                arch,
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
                "https://adoptium.net/temurin/tck-affidavit/"
            )
        }
        .toList()
}
