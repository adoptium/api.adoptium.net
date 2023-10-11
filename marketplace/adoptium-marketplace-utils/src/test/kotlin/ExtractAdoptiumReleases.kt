import com.fasterxml.jackson.module.kotlin.readValue
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.parser.VersionParser
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
        val VERSIONS = listOf(8, 11, 17, 21)
    }

    //@Disabled("For manual execution")
    @Test
    fun buildRepo() {
        ExtractReleases().buildRepo(
            VERSIONS,
            { version -> "https://api.adoptium.net/v3/assets/feature_releases/${version}/ga?page_size=50&vendor=eclipse" },
            { release -> toMarketplaceRelease(release, toMarketplaceBinaries(release)) },
            "/tmp/adoptiumRepo",
            true
        )
    }

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
