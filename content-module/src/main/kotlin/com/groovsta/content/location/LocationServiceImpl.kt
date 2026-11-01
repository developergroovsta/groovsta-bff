package com.groovsta.content.location

import com.groovsta.content.config.LocationProperties
import com.groovsta.content.dto.ContentFeedItemResponse
import com.groovsta.content.dto.LocationContentPageResponse
import com.groovsta.content.dto.LocationContentType
import com.groovsta.content.dto.LocationSearchResponse
import com.groovsta.content.dto.LocationSearchSource
import com.groovsta.content.dto.LocationSummaryResponse
import com.groovsta.content.dto.ResolveLocationRequest
import com.groovsta.content.dto.toSummaryResponse
import com.groovsta.content.model.ContentItem
import com.groovsta.content.model.ContentKind
import com.groovsta.content.model.ContentLocation
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.LocationProvider
import com.groovsta.content.model.LocationSearchCache
import com.groovsta.content.model.Post
import com.groovsta.content.model.Reel
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.repository.ContentLocationRepository
import com.groovsta.content.repository.LocationSearchCacheRepository
import com.groovsta.content.repository.SavedContentRepository
import com.groovsta.content.service.LocationService
import com.groovsta.content.service.PostService
import com.groovsta.content.service.ReelService
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.service.ProfileService
import com.groovsta.identity.service.SocialGraphService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class LocationServiceImpl(
    private val locationRepository: ContentLocationRepository,
    private val searchCacheRepository: LocationSearchCacheRepository,
    private val contentRepository: ContentItemRepository,
    private val savedContentRepository: SavedContentRepository,
    private val postService: PostService,
    private val reelService: ReelService,
    private val profileService: ProfileService,
    private val socialGraphService: SocialGraphService,
    private val olaClient: OlaMapsLocationClient,
    private val properties: LocationProperties
) : LocationService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val providerRateWindows = ConcurrentHashMap<UUID, ProviderRateWindow>()

    @Transactional
    override fun search(
        accountId: UUID,
        query: String,
        latitude: Double?,
        longitude: Double?,
        limit: Int
    ): LocationSearchResponse {
        val normalized = normalizeQuery(query)
        require(normalized.length >= properties.searchMinLength) {
            "Location search requires at least ${properties.searchMinLength} characters"
        }
        val pageLimit = pageLimit(limit)
        val local = locationRepository.searchLocal(normalized, PageRequest.of(0, pageLimit))
        if (local.size >= pageLimit) {
            log.info("location.search local_hit accountId={} queryLength={} resultCount={}", accountId, normalized.length, local.size)
            return LocationSearchResponse(local.map(ContentLocation::toSummaryResponse), LocationSearchSource.LOCAL, cacheHit = true)
        }

        val cacheKey = searchCacheKey(normalized, latitude, longitude)
        val now = Instant.now()
        val cached = searchCacheRepository.findByCacheKeyAndExpiresAtAfter(cacheKey, now).orElse(null)
        if (cached != null) {
            val cachedLocations = cached.locationIds.toUuidList()
                .let { ids -> locationRepository.findAllById(ids).associateBy(ContentLocation::id).let { byId -> ids.mapNotNull(byId::get) } }
            val combined = (local + cachedLocations).distinctBy(ContentLocation::id).take(pageLimit)
            log.info("location.search cache_hit accountId={} queryLength={} resultCount={}", accountId, normalized.length, combined.size)
            return LocationSearchResponse(combined.map(ContentLocation::toSummaryResponse), LocationSearchSource.CACHE, cacheHit = true)
        }

        val providerLocations = if (providerAvailable()) {
            enforceProviderRateLimit(accountId)
            runCatching {
                olaClient.autocomplete(normalized, latitude, longitude, pageLimit).map(::upsert)
            }.getOrElse {
                if (local.isNotEmpty()) {
                    log.warn(
                        "location.search provider_failed_returning_local accountId={} queryLength={} localCount={} errorType={}",
                        accountId,
                        normalized.length,
                        local.size,
                        it.javaClass.simpleName
                    )
                    return LocationSearchResponse(
                        local.map(ContentLocation::toSummaryResponse),
                        LocationSearchSource.LOCAL,
                        cacheHit = true
                    )
                }
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Location provider is not available")
            }
        } else {
            emptyList()
        }
        val combined = (local + providerLocations)
            .distinctBy(ContentLocation::id)
            .take(pageLimit)
        saveCache(cacheKey, combined.map(ContentLocation::id), now.plus(properties.searchCacheTtl))
        log.info("location.search provider_call accountId={} queryLength={} providerCount={} returnedCount={}",
            accountId, normalized.length, providerLocations.size, combined.size)
        return LocationSearchResponse(combined.map(ContentLocation::toSummaryResponse), LocationSearchSource.PROVIDER, cacheHit = false)
    }

    @Transactional
    override fun reverse(accountId: UUID, latitude: Double, longitude: Double): LocationSummaryResponse {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        val cacheKey = reverseCacheKey(latitude, longitude)
        val now = Instant.now()
        val cached = searchCacheRepository.findByCacheKeyAndExpiresAtAfter(cacheKey, now).orElse(null)
        if (cached != null) {
            val location = cached.locationIds.toUuidList().firstOrNull()
                ?.let { locationRepository.findById(it).orElse(null) }
            if (location != null) {
                log.info("location.reverse cache_hit accountId={} locationId={}", accountId, location.id)
                return location.toSummaryResponse()
            }
        }
        if (!providerAvailable()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Location provider is not available")
        }
        enforceProviderRateLimit(accountId)
        val candidate = olaClient.reverse(latitude, longitude)
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Location provider is not available")
        val location = upsert(candidate)
        saveCache(cacheKey, listOf(location.id), now.plus(properties.reverseCacheTtl))
        log.info("location.reverse provider_call accountId={} locationId={}", accountId, location.id)
        return location.toSummaryResponse()
    }

    @Transactional
    override fun resolve(accountId: UUID, request: ResolveLocationRequest): LocationSummaryResponse {
        val existing = locationRepository.findByProviderAndProviderPlaceId(request.provider, request.providerPlaceId)
            .orElse(null)
        if (existing != null && existing.latitude != null && existing.longitude != null) {
            log.info("location.resolve local_hit accountId={} locationId={}", accountId, existing.id)
            return existing.toSummaryResponse()
        }
        val candidate = if (providerAvailable()) {
            enforceProviderRateLimit(accountId)
            when (request.provider) {
                LocationProvider.OLA -> olaClient.details(request.providerPlaceId)
            }
        } else {
            null
        } ?: existing?.let {
            LocationCandidate(
                provider = it.provider,
                providerPlaceId = requireNotNull(it.providerPlaceId),
                name = it.name,
                formattedAddress = it.formattedAddress,
                latitude = it.latitude,
                longitude = it.longitude,
                city = it.city,
                state = it.state,
                country = it.country,
                postalCode = it.postalCode,
                metadataJson = it.metadataJson
            )
        } ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Location provider is not available")
        val location = upsert(candidate)
        log.info("location.resolve provider_call accountId={} locationId={}", accountId, location.id)
        return location.toSummaryResponse()
    }

    @Transactional(readOnly = true)
    override fun get(locationId: UUID): LocationSummaryResponse =
        locationRepository.findById(locationId).orElseThrow { notFound() }.toSummaryResponse()

    @Transactional(readOnly = true)
    override fun content(
        viewerAccountId: UUID?,
        locationId: UUID,
        type: LocationContentType,
        cursor: String?,
        limit: Int
    ): LocationContentPageResponse {
        val location = locationRepository.findById(locationId).orElseThrow { notFound() }
        val pageSize = limit.coerceIn(1, 50)
        val before = cursor?.let(LocationCursor::decode)?.before ?: Instant.now().plusMillis(1)
        val contentKind = when (type) {
            LocationContentType.ALL -> null
            LocationContentType.POST -> ContentKind.POST.name
            LocationContentType.REEL -> ContentKind.REEL.name
        }
        val fetched = contentRepository.findVisibleByLocation(
            locationId = locationId,
            viewerAccountId = viewerAccountId,
            contentKind = contentKind,
            status = ContentStatus.PUBLISHED,
            activeStatus = AccountStatus.ACTIVE,
            before = before,
            pageable = PageRequest.of(0, pageSize + 1)
        )
        val page = fetched.take(pageSize)
        return LocationContentPageResponse(
            location = location.toSummaryResponse(),
            items = feedItems(page, viewerAccountId),
            nextCursor = page.lastOrNull()
                ?.takeIf { fetched.size > pageSize }
                ?.let { LocationCursor(requireNotNull(it.publishedAt)).encode() }
        )
    }

    private fun feedItems(content: List<ContentItem>, viewerAccountId: UUID?): List<ContentFeedItemResponse> {
        if (content.isEmpty()) return emptyList()
        val authors = profileService.summaries(content.mapTo(mutableSetOf(), ContentItem::ownerAccountId))
        val postResponses = postService.getAll(viewerAccountId, content.filterIsInstance<Post>().map(Post::id))
        val reelResponses = reelService.getAll(viewerAccountId, content.filterIsInstance<Reel>().map(Reel::id))
        val savedByContentId = if (viewerAccountId == null) {
            emptyMap()
        } else {
            savedContentRepository.findByAccountIdAndContentIdIn(viewerAccountId, content.map(ContentItem::id))
                .associateBy { it.contentId }
        }
        val followingIds = viewerAccountId?.let(socialGraphService::followingAccountIds).orEmpty()
        return content.map { item ->
            val publishedAt = requireNotNull(item.publishedAt)
            val author = requireNotNull(authors[item.ownerAccountId]) { "Published content owner must have a profile" }
            when (item) {
                is Post -> {
                    val post = requireNotNull(postResponses[item.id])
                    ContentFeedItemResponse(
                        id = item.id,
                        type = ContentKind.POST,
                        author = author,
                        isFollowingAuthor = viewerAccountId?.let { item.ownerAccountId in followingIds },
                        canShareToStory = post.canShareToStory,
                        saved = viewerAccountId?.let { item.id in savedByContentId },
                        savedAt = savedByContentId[item.id]?.createdAt,
                        post = post,
                        reel = null,
                        publishedAt = publishedAt
                    )
                }
                is Reel -> {
                    val reel = requireNotNull(reelResponses[item.id])
                    ContentFeedItemResponse(
                        id = item.id,
                        type = ContentKind.REEL,
                        author = author,
                        isFollowingAuthor = viewerAccountId?.let { item.ownerAccountId in followingIds },
                        canShareToStory = reel.canShareToStory,
                        saved = viewerAccountId?.let { item.id in savedByContentId },
                        savedAt = savedByContentId[item.id]?.createdAt,
                        post = null,
                        reel = reel,
                        publishedAt = publishedAt
                    )
                }
                else -> error("Unsupported content type: ${item::class.simpleName}")
            }
        }
    }

    private fun upsert(candidate: LocationCandidate): ContentLocation {
        val now = Instant.now()
        val location = locationRepository.findByProviderAndProviderPlaceId(candidate.provider, candidate.providerPlaceId)
            .orElseGet { ContentLocation(provider = candidate.provider, providerPlaceId = candidate.providerPlaceId) }
        location.name = candidate.name.trim().take(200)
        location.formattedAddress = candidate.formattedAddress?.trim()?.take(1000)
        location.latitude = candidate.latitude ?: location.latitude
        location.longitude = candidate.longitude ?: location.longitude
        location.city = candidate.city ?: location.city
        location.state = candidate.state ?: location.state
        location.country = candidate.country ?: location.country
        location.postalCode = candidate.postalCode ?: location.postalCode
        location.metadataJson = candidate.metadataJson ?: location.metadataJson
        location.lastRefreshedAt = now
        location.updatedAt = now
        return locationRepository.save(location)
    }

    private fun saveCache(cacheKey: String, locationIds: List<UUID>, expiresAt: Instant) {
        val now = Instant.now()
        val cache = searchCacheRepository.findByCacheKey(cacheKey)
            .orElseGet { LocationSearchCache(cacheKey = cacheKey) }
        cache.locationIds = locationIds.joinToString(",")
        cache.expiresAt = expiresAt
        cache.updatedAt = now
        searchCacheRepository.save(cache)
    }

    private fun pageLimit(limit: Int) = limit.coerceIn(1, properties.searchLimit.coerceIn(1, 50))

    private fun normalizeQuery(query: String) = query.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private fun searchCacheKey(query: String, latitude: Double?, longitude: Double?): String =
        listOf("search", query, coordinateBucket(latitude, longitude), properties.language).joinToString("|")

    private fun reverseCacheKey(latitude: Double, longitude: Double): String =
        listOf("reverse", "%.4f".format(Locale.ROOT, latitude), "%.4f".format(Locale.ROOT, longitude), properties.language)
            .joinToString("|")

    private fun coordinateBucket(latitude: Double?, longitude: Double?): String =
        if (latitude == null || longitude == null) "global"
        else "%.2f,%.2f".format(Locale.ROOT, latitude, longitude)

    private fun providerAvailable() = properties.providerEnabled && properties.providerApiKey.isNotBlank()

    private fun enforceProviderRateLimit(accountId: UUID) {
        val limit = properties.providerRateLimitPerMinute
        if (limit <= 0) return
        val now = Instant.now()
        val window = providerRateWindows.compute(accountId) { _, current ->
            if (current == null || current.windowStart.plusSeconds(60).isBefore(now)) {
                ProviderRateWindow(now, 1)
            } else {
                current.count += 1
                current
            }
        } ?: ProviderRateWindow(now, 1)
        if (window.count > limit) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Location provider rate limit exceeded")
        }
    }

    private fun String.toUuidList(): List<UUID> =
        split(',').mapNotNull { value -> value.takeIf(String::isNotBlank)?.let(UUID::fromString) }

    private fun notFound(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Location was not found")

    private data class ProviderRateWindow(val windowStart: Instant, var count: Int)

    private data class LocationCursor(val before: Instant) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(before.toString().toByteArray(Charsets.UTF_8))

        companion object {
            fun decode(value: String): LocationCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid location content cursor") }
                return LocationCursor(
                    runCatching { Instant.parse(decoded) }
                        .getOrElse { throw IllegalArgumentException("Invalid location content cursor") }
                )
            }
        }
    }
}
