package com.weare5stones.keycloak.groupmgmt.util

data class PageMeta(
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
    val totalPages: Int
)

data class PagedResponse<T>(
    val data: List<T>,
    val meta: PageMeta
)

data class PaginationParams(
    val page: Int,
    val pageSize: Int,
    val offset: Int
)

fun paginationParams(page: Int, pageSize: Int): PaginationParams {
    val effectivePage = maxOf(page, 1)
    val effectivePageSize = pageSize.coerceIn(1, 100)
    return PaginationParams(
        page = effectivePage,
        pageSize = effectivePageSize,
        offset = (effectivePage - 1) * effectivePageSize
    )
}

fun totalPages(totalCount: Long, pageSize: Int): Int {
    return if (totalCount == 0L) 1 else ((totalCount + pageSize - 1) / pageSize).toInt()
}

fun <T> pagedResponse(data: List<T>, totalCount: Long, params: PaginationParams): PagedResponse<T> {
    return PagedResponse(
        data = data,
        meta = PageMeta(
            page = params.page,
            pageSize = params.pageSize,
            totalCount = totalCount,
            totalPages = totalPages(totalCount, params.pageSize)
        )
    )
}
