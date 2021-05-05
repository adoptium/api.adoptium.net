package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.APIDataStoreImpl
import net.adoptopenjdk.api.v3.dataSources.SortMethod
import net.adoptopenjdk.api.v3.dataSources.SortOrder
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.models.Releases
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.Binary
import net.adoptopenjdk.api.v3.models.DateTime
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.Installer
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Package
import net.adoptopenjdk.api.v3.models.Project
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.models.VersionData
import net.adoptopenjdk.api.v3.routes.AssetsResource
import net.adoptopenjdk.api.v3.routes.ReleaseEndpoint
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class AssetsResourceFeatureReleasePathSortOrderTest : FrontendTest() {

    val apiDataStore = ApiDataStoreStub(createRepo())
    var assetResource: AssetsResource = AssetsResource(apiDataStore, ReleaseEndpoint(apiDataStore))

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
                Project.jdk
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
                                    arrayOf(binary), 2, Vendor.adoptopenjdk,
                                    VersionData(8, 0, 242, "b", null, 4, "b", "8u242-b04_openj9-0.18.0-m1")
                                ),

                                Release(
                                    "bar", ReleaseType.ga, "a", "bar",
                                    DateTime(ZonedDateTime.of(2010, 1, 2, 1, 1, 0, 0, TimeSource.ZONE)),
                                    DateTime(ZonedDateTime.of(2010, 1, 2, 1, 1, 0, 0, TimeSource.ZONE)),
                                    arrayOf(binary), 2, Vendor.adoptopenjdk,
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
    fun doesSortObaySortMethod(apiDatastore: APIDataStoreImpl) {
        runBlocking {
            assertEquals("bar", getRelease(SortOrder.DESC, SortMethod.DATE)[0].id)
            assertEquals("foo", getRelease(SortOrder.DESC, SortMethod.DEFAULT)[0].id)
            assertEquals("foo", getRelease(SortOrder.DESC, null)[0].id)
        }
    }

    private fun getRelease(sortOrder: SortOrder, sortMethod: SortMethod?): List<Release> {
        return assetResource.get(
            version = 8, release_type = ReleaseType.ga, sortOrder = sortOrder, sortMethod = sortMethod,
            arch = null, heap_size = null, jvm_impl = null, image_type = null, os = null, page = null, pageSize = null, project = null, vendor = null, before = null
        )
    }

    @Test
    fun doesSortOrderIgnoreOpt() {
        runBlocking {
            val releases = getRelease(SortOrder.DESC, null)
            assertEquals("bar", releases.get(1).id)
        }
    }
}
