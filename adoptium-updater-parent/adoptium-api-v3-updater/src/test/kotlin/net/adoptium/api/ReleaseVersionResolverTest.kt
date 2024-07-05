package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.UpdatableVersionSupplierStub
import net.adoptium.api.v3.dataSources.ReleaseVersionResolver
import net.adoptium.api.v3.models.ReleaseInfo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReleaseVersionResolverTest : BaseTest() {

    private fun getReleaseVersionResolver(): ReleaseVersionResolver {
        return ReleaseVersionResolver(UpdatableVersionSupplierStub())
    }

    @Test
    fun availableVersionsIsCorrect() {
        check { releaseInfo ->
            //remove the releases that do not have ga's
            val versions = AdoptReposTestDataGenerator.generate().repos.keys
                .filter { it != 18 }
                .toTypedArray()

            releaseInfo.available_releases.contentEquals(versions)
        }
    }

    @Test
    fun availableLtsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.available_lts_releases.contentEquals(arrayOf(8, 11))
        }
    }

    @Test
    fun mostRecentLtsIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_lts == 11
        }
    }

    @Test
    fun mostRecentFeatureReleaseIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_feature_release == 12
        }
    }

    @Test
    fun mostRecentFeatureVersionIsCorrect() {
        check { releaseInfo ->
            releaseInfo.most_recent_feature_version == 18
        }
    }

    private fun check(matcher: (ReleaseInfo) -> Boolean) {
        runBlocking {
            val releaseVersionResolver = getReleaseVersionResolver()
            val info = releaseVersionResolver.formReleaseInfo(adoptRepos)
            assertTrue(matcher(info))
        }
    }

    @Test
    fun tipVersionIsCorrect() {
        check { releaseInfo ->
            releaseInfo.tip_version == 15
        }
    }
}
