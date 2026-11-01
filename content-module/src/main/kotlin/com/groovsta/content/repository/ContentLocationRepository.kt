package com.groovsta.content.repository

import com.groovsta.content.model.ContentLocation
import com.groovsta.content.model.LocationProvider
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface ContentLocationRepository : JpaRepository<ContentLocation, UUID> {
    fun findByProviderAndProviderPlaceId(
        provider: LocationProvider,
        providerPlaceId: String
    ): Optional<ContentLocation>

    @Query(
        """
        select location
        from ContentLocation location
        where lower(location.name) like lower(concat('%', :query, '%'))
           or lower(coalesce(location.formattedAddress, '')) like lower(concat('%', :query, '%'))
           or lower(coalesce(location.city, '')) like lower(concat('%', :query, '%'))
           or lower(coalesce(location.state, '')) like lower(concat('%', :query, '%'))
        order by
            case
                when lower(location.name) like lower(concat(:query, '%')) then 0
                else 1
            end asc,
            location.name asc
        """
    )
    fun searchLocal(
        @Param("query") query: String,
        pageable: Pageable
    ): List<ContentLocation>
}
