package net.adoptopenjdk.api

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.HttpClientFactory
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UrlRequest
import net.adoptopenjdk.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicStatusLine
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@AddPackages(value = [GitHubHtmlClient::class, DefaultUpdaterHtmlClient::class, HttpClientFactory::class])
class AdoptMetadataVersionParsingTest : BaseTest() {

    val client: UpdaterHtmlClient = object : UpdaterHtmlClient {
        override suspend fun get(url: String): String? {
            return """
                        {
                            "WARNING": "THIS METADATA FILE IS STILL IN ALPHA DO NOT USE ME",
                            "os": "windows",
                            "arch": "x86-32",
                            "variant": "openj9",
                            "version": {
                                "minor": 0,
                                "security": 242,
                                "pre": null,
                                "adopt_build_number": 1,
                                "major": 8,
                                "version": "1.8.0_242-202001081700-b06",
                                "semver": "8.0.242+6.1.202001081700",
                                "build": 6,
                                "opt": "202001081700"
                            },
                            "scmRef": "",
                            "version_data": "jdk8u",
                            "binary_type": "jre",
                            "sha256": "dc755cf762c867d4c71b782b338d2dc1500b468ab01adbf88620b5ae55eef42a"
                        }
            """.trimIndent()
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

    @Test
    fun usesMetadataForVersion() {
        runBlocking {
            val json = String(this.javaClass.classLoader.getResourceAsStream("example-release.json").readAllBytes())
            val release = JsonMapper.mapper.readValue(json, GHRelease::class.java)
            val adoptRelease = createAdoptReleaseMapper(client).toAdoptRelease(release)

            assertEquals(242, adoptRelease.result!!.first().version_data.security)
        }
    }
}
