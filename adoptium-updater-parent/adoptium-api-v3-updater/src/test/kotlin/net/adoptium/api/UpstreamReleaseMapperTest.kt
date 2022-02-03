package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.upstream.UpstreamReleaseMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class UpstreamReleaseMapperTest {

    @Test
    fun parsedSourceHasCorrectSourceInfo() {
        runBlocking {
            val ghRelease = getRelease()

            val release = UpstreamReleaseMapper.toAdoptRelease(ghRelease)

            assertEquals(ghRelease.releaseAssets.assets[0].downloadUrl, release.result!!.first().source!!.link)
            assertEquals(ghRelease.releaseAssets.assets[0].name, release.result!!.first().source!!.name)
            assertEquals(ghRelease.releaseAssets.assets[0].size, release.result!!.first().source!!.size)
        }
    }

    @Test
    fun statsOnlyLooksAtBinaryAssets() {
        runBlocking {
            val ghRelease = getRelease()

            val release = UpstreamReleaseMapper.toAdoptRelease(ghRelease)

            assertEquals(1, release.result!!.first().download_count)
        }
    }

    private fun getRelease(): GHRelease {
        val source = GHAssets(
            listOf(
                GHAsset(
                    "OpenJDK8U-sources_8u232b09.tar.gz",
                    1, "", 1, "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "OpenJDK8U-sources_8u232b09.tar.gz.json",
                    1, "", 1, "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "OpenJDK8U-sources_8u232b09.tar.gz.sha256.txt",
                    1, "", 1, "2013-02-27T19:35:32Z"
                )
            ),
            PageInfo(false, ""),
            3
        )

        return GHRelease(GitHubId("1"), "OpenJDK 8u232 GA Release", true, "2013-02-27T19:35:32Z", "2013-02-27T19:35:32Z", source, "8", "a-url")
    }
}
