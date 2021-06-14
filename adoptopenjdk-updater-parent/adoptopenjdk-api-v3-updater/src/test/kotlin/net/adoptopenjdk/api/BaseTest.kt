package net.adoptopenjdk.api

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import net.adoptopenjdk.api.testDoubles.InMemoryApiPersistence
import net.adoptopenjdk.api.testDoubles.InMemoryInternalDbStore
import net.adoptopenjdk.api.v3.dataSources.APIDataStoreImpl
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UrlRequest
import net.adoptopenjdk.api.v3.dataSources.github.CachedGitHubHtmlClient
import net.adoptopenjdk.api.v3.mapping.ReleaseMapper
import net.adoptopenjdk.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptopenjdk.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptopenjdk.api.v3.models.Vendor
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicStatusLine
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAlternatives
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.extension.ExtendWith

@EnableAutoWeld
@ExtendWith(MockKExtension::class)
@AddPackages(value = [InMemoryApiPersistence::class, InMemoryInternalDbStore::class, APIDataStoreImpl::class])
@EnableAlternatives
abstract class BaseTest {

    companion object {
        @JvmStatic
        val adoptRepos = AdoptReposTestDataGenerator.generate()

        @JvmStatic
        @BeforeAll
        fun startDb() {
            System.setProperty("GITHUB_TOKEN", "stub-token")
        }
    }

    fun mockkHttpClient(): UpdaterHtmlClient {
        return object : UpdaterHtmlClient {
            override suspend fun get(url: String): String? {
                if (url.endsWith("sha256.txt")) {
                    return "CAFE123 IAmAChecksum"
                }

                return null
            }

            override suspend fun getFullResponse(request: UrlRequest): HttpResponse? {
                val metadataResponse = mockk<HttpResponse>()

                val entity = mockk<HttpEntity>()
                every { entity.content } returns get(request.url)?.byteInputStream()
                every { metadataResponse.statusLine } returns BasicStatusLine(ProtocolVersion("", 1, 1), 200, "")
                every { metadataResponse.entity } returns entity
                every { metadataResponse.getFirstHeader("Last-Modified") } returns BasicHeader("Last-Modified", "Thu, 01 Jan 1970 00:00:00 GMT")
                return metadataResponse
            }
        }
    }

    protected fun createAdoptReleaseMapper(client: UpdaterHtmlClient = mockkHttpClient()): ReleaseMapper {
        val cachedGitHubHtmlClient = CachedGitHubHtmlClient(InMemoryInternalDbStore(), client)
        return AdoptReleaseMapperFactory(AdoptBinaryMapper(cachedGitHubHtmlClient), cachedGitHubHtmlClient).get(Vendor.getDefault())
    }
}
