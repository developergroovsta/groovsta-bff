package com.groovsta.notification.repository

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Types
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PushDeliveryClaimRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    @Transactional
    fun claim(now: Instant, lockedUntil: Instant, limit: Int): List<UUID> =
        jdbcTemplate.queryForList(
            """
            WITH candidates AS (
                SELECT id
                FROM notification_push_deliveries
                WHERE (
                    status IN ('PENDING', 'RETRY')
                    AND next_attempt_at <= :now
                ) OR (
                    status = 'PROCESSING'
                    AND locked_until <= :now
                )
                ORDER BY next_attempt_at, created_at, id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            UPDATE notification_push_deliveries delivery
            SET status = 'PROCESSING',
                attempt_count = delivery.attempt_count + 1,
                locked_until = :lockedUntil,
                updated_at = :now
            FROM candidates
            WHERE delivery.id = candidates.id
            RETURNING delivery.id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("now", now.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("lockedUntil", lockedUntil.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("limit", limit, Types.INTEGER),
            UUID::class.java
        )
}
