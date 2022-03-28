package net.adoptium.marketplace.server.frontend.routes

import net.adoptium.api.marketplace.parser.maven.VersionRange
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.schema.Architecture
import net.adoptium.marketplace.schema.Binary
import net.adoptium.marketplace.schema.CLib
import net.adoptium.marketplace.schema.ImageType
import net.adoptium.marketplace.schema.JvmImpl
import net.adoptium.marketplace.schema.OperatingSystem
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.server.frontend.filters.BinaryFilter
import net.adoptium.marketplace.server.frontend.filters.ReleaseFilter
import net.adoptium.marketplace.server.frontend.filters.ReleaseSorter
import net.adoptium.marketplace.server.frontend.filters.VersionRangeFilter
import net.adoptium.marketplace.server.frontend.models.SortMethod
import net.adoptium.marketplace.server.frontend.models.SortOrder
import net.adoptium.marketplace.server.frontend.versions.FailedToParse
import java.util.function.Predicate
import javax.inject.Inject
import javax.inject.Singleton
import javax.ws.rs.BadRequestException

@Singleton
class ReleaseEndpoint
@Inject
constructor(private val apiDataStore: APIDataStore) {
    suspend fun getReleases(
        vendor: Vendor,
        sortOrder: SortOrder?,
        sortMethod: SortMethod?,
        version: String?,
        lts: Boolean?,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?,
        jvm_impl: JvmImpl?,
        cLib: CLib?
    ): Sequence<Release> {
        val order = sortOrder ?: SortOrder.DESC
        val releaseSortMethod = sortMethod ?: SortMethod.DEFAULT

        val range = try {
            VersionRangeFilter(version)
        } catch (e: VersionRange.InvalidVersionRange) {
            throw BadRequestException("Invalid version range", e)
        } catch (e: FailedToParse) {
            throw BadRequestException("Invalid version string", e)
        }

        val releaseFilter = ReleaseFilter(vendor = vendor, versionRange = range, lts = lts)
        val binaryFilter = BinaryFilter(os = os, arch = arch, imageType = image_type, jvmImpl = jvm_impl, cLib = cLib)

        return getReleases(vendor, releaseFilter, binaryFilter, order, releaseSortMethod)
    }

    suspend fun getReleases(
        vendor: Vendor,
        releaseFilter: Predicate<Release>,
        binaryFilter: Predicate<Binary>,
        order: SortOrder,
        sortMethod: SortMethod): Sequence<Release> {
        return apiDataStore
            .getReleases(vendor)
            .getAllReleases()
            .releases
            .filter {
                releaseFilter.test(it)
            }
            .map { release ->
                Release(
                    release,
                    release.binaries.filter { binaryFilter.test(it) }
                )
            }
            .sortedWith(ReleaseSorter.getComparator(order, sortMethod))
            .asSequence()
    }
}
