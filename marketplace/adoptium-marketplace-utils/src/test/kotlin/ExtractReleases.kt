import com.fasterxml.jackson.module.kotlin.readValue
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.parser.VersionParser
import net.adoptium.marketplace.client.MarketplaceMapper
import net.adoptium.marketplace.schema.IndexFile
import net.adoptium.marketplace.schema.ReleaseList
import org.eclipse.jetty.client.HttpClient
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import java.nio.file.Paths

class ExtractReleases {

    fun buildRepo(
        versions: List<Int>,
        apiUrl: (Int) -> String,
        dataMapper: (releases: Release) -> net.adoptium.marketplace.schema.Release,
        outputDir: String,
        signAssets: Boolean) {
        val httpClient = HttpClient()
        httpClient.isFollowRedirects = true
        httpClient.start()

        val dir = File(outputDir).toPath()
        dir.toFile().mkdirs()

        // Write ./index.json file
        createTopLevelIndexFile(dir, versions)

        // Only doing LTS for now
        versions
            .map { version ->
                // Get directory to write to, i.e `./8/`
                val versionDir = Path.of(dir.toFile().absolutePath, "$version").toFile()
                versionDir.mkdirs()

                // Fetch releases in Adoptiums (NOT marketplace) schema
                val releases = getAdoptiumReleases(apiUrl, httpClient, version)

                // Represent Adoptium releases in Marketplace schema
                val marketplaceReleases = convertToMarketplaceSchema(releases, dataMapper)

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

        if (signAssets) {
            // Sign new files
            SignTestAssets.sign(dir.toFile().absolutePath)
        }
    }

    private fun createTopLevelIndexFile(dir: Path, versions: List<Int>) {
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
            versions
                .map { "$it/index.json" },
            emptyList()
        )

        val indexfw = FileWriter(Paths.get(dir.toFile().absolutePath, "index.json").toFile())
        indexfw.use {
            it.write(MarketplaceMapper.repositoryObjectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(indexFile))
        }
    }

    private fun convertToMarketplaceSchema(releases: List<Release>, dataMapper: (releases: Release) -> net.adoptium.marketplace.schema.Release): List<ReleaseList> {
        val marketplaceReleases = releases
            .map { release ->
                ReleaseList(listOf(dataMapper(release)))
            }
            .toList()
        return marketplaceReleases
    }

    private fun filterValidVersions(release: Release): Boolean {
        val validVersion = when (release.version_data.major) {
            11 -> {
                VersionParser.parse("11.0.14.1+1")
            }
            17 -> {
                VersionParser.parse("17.0.2+8")
            }
            else -> {
                VersionParser.parse("jdk8u322-b06")
            }
        }

        return release.version_data >= validVersion
    }

    private fun getAdoptiumReleases(apiUrl: (Int) -> String, httpClient: HttpClient, version: Int): List<Release> {
        // Possibly might need to check next page...one day
        val response = httpClient.GET(apiUrl(version))

        val releases = JsonMapper.mapper.readValue<List<Release>>(response.content)

        return releases
            .map { release ->
                val filteredBinaries = release.binaries.filter {
                    it.image_type == net.adoptium.api.v3.models.ImageType.jdk ||
                        it.image_type == net.adoptium.api.v3.models.ImageType.jre
                }

                Release(release, filteredBinaries.toTypedArray())
            }
            .filter { filterValidVersions(it) }
    }

    private fun toFileName(it: net.adoptium.marketplace.schema.Release) = it
        .releaseName
        .replace("+", "_")
        .replace(".", "_")
        .replace("-", "_")
        .plus(".json")

}
