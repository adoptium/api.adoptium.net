import com.fasterxml.jackson.module.kotlin.readValue
import net.adoptium.api.v3.models.Release
import net.adoptium.marketplace.schema.Architecture
import net.adoptium.marketplace.schema.Binary
import net.adoptium.marketplace.schema.CLib
import net.adoptium.marketplace.schema.Distribution
import net.adoptium.marketplace.schema.ImageType
import net.adoptium.marketplace.schema.Installer
import net.adoptium.marketplace.schema.JvmImpl
import net.adoptium.marketplace.schema.OpenjdkVersionData
import net.adoptium.marketplace.schema.OperatingSystem
import net.adoptium.marketplace.schema.Package
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.SourcePackage
import net.adoptium.marketplace.schema.Vendor
import org.junit.jupiter.api.Test
import java.util.*

class ExtractIbmReleases {

    companion object {
        val VERSIONS = listOf(11, 17)
    }

    //@Disabled("For manual execution")
    @Test
    fun buildRepo() {
        ExtractReleases().buildRepo(
            VERSIONS,
            { version -> "https://ibm.com/semeru-runtimes/api/v3/assets/feature_releases/${version}/ga?vendor=ibm_ce&page_size=100" },
            { release -> toMarketplaceRelease(release, toMarketplaceBinaries(release)) },
            "/tmp/ibmRepo",
            false
        )
    }

    private fun toMarketplaceRelease(release: Release, binaries: List<Binary>): net.adoptium.marketplace.schema.Release {
        return net.adoptium.marketplace.schema.Release(
            release.release_link,
            release.release_name,
            Date.from(release.timestamp.dateTime.toInstant()),
            binaries,
            Vendor.ibm, // ENSURE YOU DO NOT RELY ON release.vendor IT IS WRONG
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

            val aqaLink = "<Insert AQA link here>"

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
                binary.scm_ref,
                Distribution.semeru,
                aqaLink,
                "<Insert TCK Affidavit Here>"
            )
        }
        .toList()

}
