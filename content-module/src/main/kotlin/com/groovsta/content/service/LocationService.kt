package com.groovsta.content.service

import com.groovsta.content.dto.LocationContentPageResponse
import com.groovsta.content.dto.LocationContentType
import com.groovsta.content.dto.LocationSearchResponse
import com.groovsta.content.dto.LocationSummaryResponse
import com.groovsta.content.dto.ResolveLocationRequest
import java.util.UUID

interface LocationService {
    fun search(accountId: UUID, query: String, latitude: Double?, longitude: Double?, limit: Int): LocationSearchResponse
    fun reverse(accountId: UUID, latitude: Double, longitude: Double): LocationSummaryResponse
    fun resolve(accountId: UUID, request: ResolveLocationRequest): LocationSummaryResponse
    fun get(locationId: UUID): LocationSummaryResponse
    fun content(
        viewerAccountId: UUID?,
        locationId: UUID,
        type: LocationContentType,
        cursor: String?,
        limit: Int
    ): LocationContentPageResponse
}
