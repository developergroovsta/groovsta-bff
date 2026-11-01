package com.groovsta.content.location

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.groovsta.content.config.LocationProperties
import com.groovsta.content.model.LocationProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriBuilder
import java.util.UUID

@Component
class OlaMapsLocationClient(
    private val properties: LocationProperties,
    builder: RestClient.Builder,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = builder.baseUrl(properties.providerBaseUrl.trimEnd('/')).build()

    fun autocomplete(input: String, latitude: Double?, longitude: Double?, limit: Int): List<LocationCandidate> {
        if (!enabled()) return emptyList()
        val requestId = UUID.randomUUID().toString()
        return runCatching {
            val body = client.get()
                .uri { builder ->
                    builder.path("/places/v1/autocomplete")
                        .queryParam("api_key", properties.providerApiKey)
                        .queryParam("input", input)
                        .queryParam("language", properties.language)
                        .applyBias(latitude, longitude)
                        .queryParam("radius", properties.searchRadiusMeters)
                        .build()
                }
                .header("X-Request-Id", requestId)
                .headers(::applyOriginHeaders)
                .retrieve()
                .body(String::class.java)
            parsePredictions(requireNotNull(body), "predictions").take(limit)
        }.onFailure {
            log.warn("location.provider.autocomplete failed requestId={} queryLength={} errorType={} message={}",
                requestId, input.length, it.javaClass.simpleName, it.message)
        }.getOrThrow()
    }

    fun details(providerPlaceId: String): LocationCandidate? {
        if (!enabled()) return null
        val requestId = UUID.randomUUID().toString()
        return runCatching {
            val body = client.get()
                .uri { builder ->
                    builder.path("/places/v1/details")
                        .queryParam("api_key", properties.providerApiKey)
                        .queryParam("place_id", providerPlaceId)
                        .queryParam("language", properties.language)
                        .build()
                }
                .header("X-Request-Id", requestId)
                .headers(::applyOriginHeaders)
                .retrieve()
                .body(String::class.java)
            val root = objectMapper.readTree(requireNotNull(body))
            parsePlace(root.path("result"), providerPlaceId, body)
        }.onFailure {
            log.warn("location.provider.details failed requestId={} providerPlaceId={} errorType={} message={}",
                requestId, providerPlaceId, it.javaClass.simpleName, it.message)
        }.getOrNull()
    }

    fun reverse(latitude: Double, longitude: Double): LocationCandidate? {
        if (!enabled()) return null
        val requestId = UUID.randomUUID().toString()
        return runCatching {
            val body = client.get()
                .uri { builder ->
                    builder.path("/places/v1/reverse-geocode")
                        .queryParam("api_key", properties.providerApiKey)
                        .queryParam("latlng", "$latitude,$longitude")
                        .queryParam("language", properties.language)
                        .build()
                }
                .header("X-Request-Id", requestId)
                .headers(::applyOriginHeaders)
                .retrieve()
                .body(String::class.java)
            val root = objectMapper.readTree(requireNotNull(body))
            root.path("results").firstOrNull()?.let { parsePlace(it, it.path("place_id").asText(null), body) }
        }.onFailure {
            log.warn("location.provider.reverse failed requestId={} errorType={} message={}",
                requestId, it.javaClass.simpleName, it.message)
        }.getOrNull()
    }

    private fun enabled() = properties.providerEnabled && properties.providerApiKey.isNotBlank()

    private fun applyOriginHeaders(headers: org.springframework.http.HttpHeaders) {
        val origin = properties.providerOrigin.trim().trimEnd('/')
        if (origin.isBlank()) return
        headers.set("Origin", origin)
        headers.set("Referer", "$origin/")
    }

    private fun UriBuilder.applyBias(latitude: Double?, longitude: Double?): UriBuilder {
        if (latitude != null && longitude != null) {
            queryParam("location", "$latitude,$longitude")
        }
        return this
    }

    private fun parsePredictions(body: String, arrayField: String): List<LocationCandidate> {
        val root = objectMapper.readTree(body)
        return root.path(arrayField).mapNotNull { prediction ->
            val providerPlaceId = prediction.path("place_id").asText(null) ?: return@mapNotNull null
            val formatting = prediction.path("structured_formatting")
            val name = formatting.path("main_text").asText(null)
                ?: prediction.path("name").asText(null)
                ?: prediction.path("terms").firstOrNull()?.path("value")?.asText(null)
                ?: prediction.path("description").asText(null)
                ?: return@mapNotNull null
            LocationCandidate(
                provider = LocationProvider.OLA,
                providerPlaceId = providerPlaceId,
                name = name,
                formattedAddress = prediction.path("description").asText(null)
                    ?: formatting.path("secondary_text").asText(null),
                latitude = prediction.path("geometry").path("location").path("lat").takeIf(JsonNode::isNumber)?.asDouble(),
                longitude = prediction.path("geometry").path("location").path("lng").takeIf(JsonNode::isNumber)?.asDouble(),
                city = null,
                state = null,
                country = null,
                postalCode = null,
                metadataJson = prediction.toString()
            )
        }
    }

    private fun parsePlace(node: JsonNode, fallbackPlaceId: String?, rawJson: String): LocationCandidate? {
        if (node.isMissingNode || node.isNull) return null
        val providerPlaceId = node.path("place_id").asText(null) ?: fallbackPlaceId ?: return null
        val name = node.path("name").asText(null)
            ?: node.path("formatted_address").asText(null)
            ?: return null
        val components = node.path("address_components")
        return LocationCandidate(
            provider = LocationProvider.OLA,
            providerPlaceId = providerPlaceId,
            name = name,
            formattedAddress = node.path("formatted_address").asText(null),
            latitude = node.path("geometry").path("location").path("lat").takeIf(JsonNode::isNumber)?.asDouble(),
            longitude = node.path("geometry").path("location").path("lng").takeIf(JsonNode::isNumber)?.asDouble(),
            city = component(components, "locality") ?: component(components, "administrative_area_level_3"),
            state = component(components, "administrative_area_level_1"),
            country = component(components, "country"),
            postalCode = component(components, "postal_code"),
            metadataJson = rawJson
        )
    }

    private fun component(components: JsonNode, type: String): String? =
        components.firstOrNull { component ->
            component.path("types").any { it.asText() == type }
        }?.path("long_name")?.asText(null)
}

data class LocationCandidate(
    val provider: LocationProvider,
    val providerPlaceId: String,
    val name: String,
    val formattedAddress: String?,
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val state: String?,
    val country: String?,
    val postalCode: String?,
    val metadataJson: String?
)
