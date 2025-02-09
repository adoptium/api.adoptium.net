package net.adoptium.api.v3

import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import kotlin.math.min

object Pagination {
    const val defaultPageSizeNum = 10
    private const val maxPageSizeNum = 20
    const val largerPageSizeNum = 50
    const val defaultPageSize = defaultPageSizeNum.toString()
    const val maxPageSize = maxPageSizeNum.toString()
    const val largerPageSize = largerPageSizeNum.toString()

    fun <T, U> formPagedResponse(data: T, uriInfo: UriInfo, pageInfo: PaginationInfo<U>): Response {
        var builder = Response
            .ok()
            .entity(data)

        if (pageInfo.next != null) {
            val nextUri = uriInfo
                .requestUriBuilder
                .replaceQueryParam("page", pageInfo.next)
                .replaceQueryParam("page_size", pageInfo.pageSize)
                .build()

            builder = builder.link(nextUri, "next")
        }

        if (pageInfo.pageCount != null) {
            builder = builder.header("PageCount", pageInfo.pageCount)
        }

        return builder.build()
    }

    fun <T> getResponseForPage(
        uriInfo: UriInfo,
        pageSize: Int?,
        page: Int?,
        releases: Sequence<T>,
        showPageCount: Boolean,
        maxPageSizeNum: Int = this.maxPageSizeNum,
    ): Response {
        val pageInfo = getPage(pageSize, page, releases, showPageCount, maxPageSizeNum)
        return formPagedResponse(pageInfo.data, uriInfo, pageInfo)
    }

    fun <T> getPage(
        pageSize: Int?,
        page: Int?,
        releases: Sequence<T>,
        showPageCount: Boolean,
        maxPageSizeNum: Int = this.maxPageSizeNum,
    ): PaginationInfo<T> {
        val pageSizeNum = min(maxPageSizeNum, (pageSize ?: defaultPageSizeNum))
        val pageNum = page ?: 0

        return try {

            var totalPages: Int? = null

            val chunked = if (showPageCount) {
                val releasesList = releases.toList()
                val seq = releasesList.chunked(pageSizeNum)

                totalPages = seq.size

                seq.asSequence()
            } else {
                releases.chunked(pageSizeNum)
            }

            val pages = chunked.drop(pageNum).take(2).toList()

            if (pages.isEmpty()) {
                throw NotFoundException("Page not available")
            }

            val hasNext = try {
                if (pages.size > 1) {
                    pages[1].isNotEmpty()
                } else {
                    false
                }
            } catch (e: IndexOutOfBoundsException) {
                false
            }

            PaginationInfo(
                if (hasNext) pageNum + 1 else null,
                pageSizeNum,
                totalPages,
                pages[0]
            )
        } catch (e: IndexOutOfBoundsException) {
            throw NotFoundException("Page not available")
        }
    }

    data class PaginationInfo<T>(
        val next: Int?,
        val pageSize: Int,
        val pageCount: Int?,

        val data: List<T>
    )
}
