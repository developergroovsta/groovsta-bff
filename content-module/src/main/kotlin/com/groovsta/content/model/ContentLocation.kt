package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_locations")
class ContentLocation(
    id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var provider: LocationProvider = LocationProvider.OLA,
    @Column(name = "provider_place_id", length = 255)
    var providerPlaceId: String? = null,
    @Column(nullable = false, length = 200)
    var name: String = "",
    @Column(name = "formatted_address", length = 1000)
    var formattedAddress: String? = null,
    @Column
    var latitude: Double? = null,
    @Column
    var longitude: Double? = null,
    @Column(length = 120)
    var city: String? = null,
    @Column(length = 120)
    var state: String? = null,
    @Column(length = 120)
    var country: String? = null,
    @Column(name = "postal_code", length = 30)
    var postalCode: String? = null,
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    var metadataJson: String? = null,
    @Column(name = "last_refreshed_at")
    var lastRefreshedAt: Instant? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
