package com.groovsta.content.dto

import com.groovsta.content.model.ContentKind
import com.groovsta.content.model.ContentLocation
import com.groovsta.content.model.LocationProvider
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LocationSummaryResponse(
    val id: UUID,
    val provider: LocationProvider,
    val providerPlaceId: String?,
    val name: String,
    val formattedAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val state: String?,
    val country: String?,
    val postalCode: String?
)

data class LocationSearchResponse(
    val items: List<LocationSummaryResponse>,
    val source: LocationSearchSource,
    val cacheHit: Boolean
)

enum class LocationSearchSource {
    LOCAL,
    CACHE,
    PROVIDER
}

data class ResolveLocationRequest(
    val provider: LocationProvider = LocationProvider.OLA,
    @NotBlank
    @Size(max = 255)
    val providerPlaceId: String
)

data class LocationContentPageResponse(
    val location: LocationSummaryResponse,
    val items: List<ContentFeedItemResponse>,
    val nextCursor: String?
)

enum class LocationContentType {
    ALL,
    POST,
    REEL
}

data class ReverseLocationRequest(
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    val latitude: Double,
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    val longitude: Double
)

fun ContentLocation.toSummaryResponse() = LocationSummaryResponse(
    id = id,
    provider = provider,
    providerPlaceId = providerPlaceId,
    name = name,
    formattedAddress = formattedAddress,
    latitude = latitude,
    longitude = longitude,
    city = city,
    state = state,
    country = country,
    postalCode = postalCode
)
