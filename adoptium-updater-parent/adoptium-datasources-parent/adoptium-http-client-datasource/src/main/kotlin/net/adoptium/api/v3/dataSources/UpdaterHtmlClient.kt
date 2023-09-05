package net.adoptium.api.v3.dataSources

import org.apache.http.HttpResponse

data class UrlRequest(
    val url: String,
    val lastModified: String? = null
)

interface UpdaterHtmlClient {
    suspend fun get(url: String): String?
    suspend fun getFullResponse(request: UrlRequest): HttpResponse?
}
