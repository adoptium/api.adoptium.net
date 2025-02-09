package net.adoptium.api

import io.mockk.mockk
import jakarta.ws.rs.core.UriInfo
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.Installer
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Package
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.routes.AssetsResource
import net.adoptium.api.v3.routes.ReleaseEndpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class AssetsResourceFeatureReleasePathSortOrderTest : FrontendTest() {

    val apiDataStore = ApiDataStoreStub(createRepo())
    var assetResource: AssetsResource = AssetsResource(apiDataStore, ReleaseEndpoint(apiDataStore, releaseFilterFactory), releaseFilterFactory)

    companion object {
        fun createRepo(): AdoptRepos {
            val binary = Binary(
                Package(
                    "a",
                    "b",
                    1L,
                    "v",
                    "c",
                    3,
                    "d",
                    "e"
                ),
                2L,
                DateTime(TimeSource.now()),
                "d",
                Installer(
                    "a",
                    "b",
                    1L,
                    "v",
                    "c",
                    4,
                    "d,",
                    "e"
                ),
                HeapSize.normal,
                OperatingSystem.linux,
                Architecture.x64,
                ImageType.jdk,
                JvmImpl.hotspot,
                Project.jdk,
                null
            )

            val repo = AdoptRepos(
                listOf(
                    FeatureRelease(
                        8,
                        Releases(
                            listOf(
                                Release(
                                    "foo", ReleaseType.ga, "a", "foo",
                                    DateTime(ZonedDateTime.of(2010, 1, 1, 1, 1, 0, 0, TimeSource.ZONE)),
                                    DateTime(ZonedDateTime.of(2010, 1, 1, 1, 1, 0, 0, TimeSource.ZONE)),
                                    arrayOf(binary), 2, Vendor.getDefault(),
                                    VersionData(8, 0, 242, "b", null, 4, "b", "8u242-b04_openj9-0.18.0-m1")
                                ),

                                Release(
                                    "bar", ReleaseType.ga, "a", "bar",
                                    DateTime(ZonedDateTime.of(2010, 1, 2, 1, 1, 0, 0, TimeSource.ZONE)),
                                    DateTime(ZonedDateTime.of(2010, 1, 2, 1, 1, 0, 0, TimeSource.ZONE)),
                                    arrayOf(binary), 2, Vendor.getDefault(),
                                    VersionData(8, 0, 242, "a", null, 4, "a", "8u242-b04_openj9-0.18.0-m1")
                                )
                            )
                        )
                    )
                )
            )
            return repo
        }
    }

    @Test
    fun doesSortObaySortMethod(apiDatastore: APIDataStore) {
        runBlocking {
            assertEquals("bar", getRelease(SortOrder.DESC, SortMethod.DATE)[0].id)
            assertEquals("foo", getRelease(SortOrder.DESC, SortMethod.DEFAULT)[0].id)
            assertEquals("foo", getRelease(SortOrder.DESC, null)[0].id)
        }
    }

    private fun getRelease(sortOrder: SortOrder, sortMethod: SortMethod?): List<Release> {
        val uriInfo = mockk<UriInfo>()

        return assetResource.get(
            version = 8, release_type = ReleaseType.ga, sortOrder = sortOrder, sortMethod = sortMethod,
            arch = null, heap_size = null, jvm_impl = null, image_type = null, os = null, page = 0, pageSize = 50, project = null, vendor = null, before = null, cLib = null,
            showPageCount = false,
            uriInfo = uriInfo
        ).entity as List<Release>
    }

    @Test
    fun doesSortOrderIgnoreOpt() {
        runBlocking {
            val releases = getRelease(SortOrder.DESC, null)
            assertEquals("bar", releases.get(1).id)
        }
    }
}
