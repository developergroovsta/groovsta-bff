package com.groovsta.rewards.service.impl

import com.groovsta.rewards.config.RewardProperties
import com.groovsta.rewards.dto.ClaimRewardRequest
import com.groovsta.rewards.dto.ClaimRewardResponse
import com.groovsta.rewards.dto.RecordReelScrollRequest
import com.groovsta.rewards.dto.RecordReelScrollResponse
import com.groovsta.rewards.dto.RewardAchievementPageResponse
import com.groovsta.rewards.dto.RewardAchievementResponse
import com.groovsta.rewards.dto.RewardProgressItemResponse
import com.groovsta.rewards.dto.RewardProgressResponse
import com.groovsta.rewards.dto.RewardSummaryResponse
import com.groovsta.rewards.service.RewardService
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import com.groovsta.shared.reward.RewardEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class RewardServiceImpl(
    private val jdbc: JdbcTemplate,
    private val properties: RewardProperties,
    private val notificationPublisher: NotificationPublisher
) : RewardService, RewardEventPublisher {

    @Transactional
    override fun signupCompleted(accountId: UUID) {
        ensureWallet(accountId)
        awardIfAbsent(
            accountId = accountId,
            type = SIGNUP_BONUS,
            milestoneKey = "signup",
            requiredCount = 1,
            currentCount = 1,
            coinAmount = properties.signupCoins.toLong(),
            achievedAt = Instant.now(),
            eligibleAt = Instant.now()
        )
    }

    @Transactional
    override fun reelPublished(ownerAccountId: UUID, contentId: UUID, publishedAt: Instant) {
        ensureWallet(ownerAccountId)
        val rawCount = count(
            """
            select count(*) from content_items
            where owner_account_id = ?
              and content_kind = 'REEL'
              and status = 'PUBLISHED'
            """,
            ownerAccountId
        )
        val completedMilestones = (rawCount / properties.reelUploadUnit).toInt()
        for (milestone in 1..completedMilestones) {
            val required = milestone * properties.reelUploadUnit
            awardIfAbsent(
                accountId = ownerAccountId,
                type = REEL_UPLOAD,
                milestoneKey = required.toString(),
                requiredCount = required.toLong(),
                currentCount = rawCount,
                coinAmount = properties.reelUploadCoins.toLong(),
                achievedAt = publishedAt,
                eligibleAt = publishedAt.plus(properties.reelUploadClaimBuffer)
            )
        }
    }

    @Transactional
    override fun followEstablished(
        followedAccountId: UUID,
        followerAccountId: UUID,
        followId: UUID,
        followedAt: Instant
    ) {
        ensureWallet(followedAccountId)
        val rawCount = followerCount(followedAccountId, null)
        properties.followerMilestones.forEach { (required, coins) ->
            if (rawCount >= required) {
                awardIfAbsent(
                    accountId = followedAccountId,
                    type = FOLLOWER_MILESTONE,
                    milestoneKey = required.toString(),
                    requiredCount = required.toLong(),
                    currentCount = rawCount,
                    coinAmount = coins.toLong(),
                    achievedAt = followedAt,
                    eligibleAt = followedAt.plus(properties.followerClaimBuffer)
                )
            }
        }
    }

    @Transactional
    override fun contentLiked(
        ownerAccountId: UUID,
        likerAccountId: UUID,
        contentId: UUID,
        likeId: UUID,
        likedAt: Instant
    ) {
        if (ownerAccountId == likerAccountId) return
        ensureWallet(ownerAccountId)
        val rawCount = contentLikeCount(ownerAccountId, null)
        awardLikeMilestones(ownerAccountId, CONTENT_LIKE_MILESTONE, rawCount, likedAt)
    }

    @Transactional
    override fun storyLiked(
        ownerAccountId: UUID,
        likerAccountId: UUID,
        storyId: UUID,
        likeId: UUID,
        likedAt: Instant
    ) {
        if (ownerAccountId == likerAccountId) return
        ensureWallet(ownerAccountId)
        val rawCount = storyLikeCount(ownerAccountId, null)
        awardLikeMilestones(ownerAccountId, STORY_LIKE_MILESTONE, rawCount, likedAt)
    }

    @Transactional(readOnly = true)
    override fun summary(accountId: UUID): RewardSummaryResponse {
        ensureWalletReadOnly(accountId)
        val wallet = wallet(accountId)
        val achievements = achievementRows(accountId, null, 100)
        return RewardSummaryResponse(
            walletBalance = wallet.balance,
            lifetimeEarned = wallet.lifetimeEarned,
            lifetimeClaimed = wallet.lifetimeClaimed,
            pendingCoins = achievements.filter { it.status == PENDING && !isClaimable(it, Instant.now()) }.sumOf { it.coinAmount },
            claimableCoins = achievements.filter { it.status == PENDING && isClaimable(it, Instant.now()) }.sumOf { it.coinAmount },
            achievements = achievements.map { response(it, Instant.now()) }
        )
    }

    @Transactional(readOnly = true)
    override fun achievements(accountId: UUID, cursor: String?, limit: Int): RewardAchievementPageResponse {
        val pageSize = limit.coerceIn(1, 50)
        val rows = achievementRows(accountId, cursor?.let(AchievementCursor::decode), pageSize + 1)
        val page = rows.take(pageSize)
        return RewardAchievementPageResponse(
            items = page.map { response(it, Instant.now()) },
            nextCursor = page.lastOrNull()
                ?.takeIf { rows.size > pageSize }
                ?.let(AchievementCursor::from)
                ?.encode()
        )
    }

    @Transactional(readOnly = true)
    override fun progress(accountId: UUID): RewardProgressResponse {
        val now = Instant.now()
        val progress = listOf(
            progressItem(accountId, SIGNUP_BONUS, "Sign-up bonus", if (hasAccount(accountId)) 1 else 0, mapOf(1 to properties.signupCoins)),
            progressItem(accountId, FOLLOWER_MILESTONE, "Follower growth", followerCount(accountId, null), properties.followerMilestones),
            progressItem(accountId, CONTENT_LIKE_MILESTONE, "Post/reel likes", contentLikeCount(accountId, null), properties.likeMilestones),
            progressItem(accountId, STORY_LIKE_MILESTONE, "Story likes", storyLikeCount(accountId, null), properties.likeMilestones),
            progressItem(accountId, REEL_SCROLL, "Reels scrolled", reelScrollCount(accountId), scrollMilestones(accountId)),
            progressItem(accountId, REEL_UPLOAD, "Reel uploads", reelUploadCount(accountId, null), reelUploadMilestones(accountId))
        )
        return RewardProgressResponse(progress)
    }

    @Transactional
    override fun claim(accountId: UUID, request: ClaimRewardRequest): ClaimRewardResponse {
        ensureWallet(accountId)
        val now = Instant.now()
        val candidates = if (request.achievementId != null) {
            listOfNotNull(achievement(accountId, request.achievementId))
        } else {
            achievementRows(accountId, null, 500).filter { it.status == PENDING }
        }
        val claimable = candidates.filter { isClaimable(it, now) }
        if (request.achievementId != null && claimable.isEmpty()) {
            throw IllegalArgumentException("Reward is not claimable yet")
        }
        var claimedCoins = 0L
        val claimed = mutableListOf<AchievementRow>()
        claimable.forEach { achievement ->
            val updated = jdbc.update(
                """
                update reward_achievements
                set status = 'CLAIMED',
                    claimed_at = ?,
                    updated_at = ?
                where id = ?
                  and account_id = ?
                  and status = 'PENDING'
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                achievement.id,
                accountId
            )
            if (updated > 0) {
                claimedCoins += achievement.coinAmount
                claimed += achievement.copy(status = CLAIMED, claimedAt = now)
                jdbc.update(
                    """
                    update reward_wallets
                    set available_balance = available_balance + ?,
                        lifetime_claimed = lifetime_claimed + ?,
                        updated_at = ?
                    where account_id = ?
                    """,
                    achievement.coinAmount,
                    achievement.coinAmount,
                    Timestamp.from(now),
                    accountId
                )
                insertLedger(accountId, achievement.id, "CLAIM_CREDIT", achievement.coinAmount, now)
            }
        }
        val balance = wallet(accountId).balance
        return ClaimRewardResponse(
            claimedCoins = claimedCoins,
            walletBalance = balance,
            claimedAchievements = claimed.map { response(it, now) }
        )
    }

    @Transactional
    override fun recordReelScroll(accountId: UUID, request: RecordReelScrollRequest): RecordReelScrollResponse {
        ensureWallet(accountId)
        val reelExists = jdbc.queryForObject(
            """
            select count(*) from content_items
            where id = ?
              and content_kind = 'REEL'
              and status = 'PUBLISHED'
            """,
            Long::class.java,
            request.contentId
        ) ?: 0L
        require(reelExists > 0) { "Published reel was not found" }
        val now = Instant.now()
        val inserted = jdbc.update(
            """
            insert into reward_reel_scrolls(account_id, content_id, scrolled_at)
            values (?, ?, ?)
            on conflict (account_id, content_id) do nothing
            """,
            accountId,
            request.contentId,
            Timestamp.from(now)
        )
        val count = reelScrollCount(accountId)
        var earned: AchievementRow? = null
        if (inserted > 0) {
            val completedMilestones = (count / properties.reelScrollUnit).toInt()
            for (milestone in 1..completedMilestones) {
                val required = milestone * properties.reelScrollUnit
                earned = awardIfAbsent(
                    accountId = accountId,
                    type = REEL_SCROLL,
                    milestoneKey = required.toString(),
                    requiredCount = required.toLong(),
                    currentCount = count,
                    coinAmount = properties.reelScrollCoins.toLong(),
                    achievedAt = now,
                    eligibleAt = now
                ) ?: earned
            }
        }
        val next = ((count / properties.reelScrollUnit) + 1) * properties.reelScrollUnit
        return RecordReelScrollResponse(
            contentId = request.contentId,
            uniqueReelsScrolled = count,
            nextMilestoneAt = next,
            newlyEarnedAchievement = earned?.let { response(it, now) }
        )
    }

    private fun awardLikeMilestones(accountId: UUID, type: String, rawCount: Long, achievedAt: Instant) {
        properties.likeMilestones.forEach { (required, coins) ->
            if (rawCount >= required) {
                awardIfAbsent(
                    accountId = accountId,
                    type = type,
                    milestoneKey = required.toString(),
                    requiredCount = required.toLong(),
                    currentCount = rawCount,
                    coinAmount = coins.toLong(),
                    achievedAt = achievedAt,
                    eligibleAt = achievedAt.plus(properties.likeClaimBuffer)
                )
            }
        }
    }

    private fun awardIfAbsent(
        accountId: UUID,
        type: String,
        milestoneKey: String,
        requiredCount: Long,
        currentCount: Long,
        coinAmount: Long,
        achievedAt: Instant,
        eligibleAt: Instant
    ): AchievementRow? {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val inserted = try {
            jdbc.update(
                """
                insert into reward_achievements(
                    id, account_id, reward_type, milestone_key, required_count, current_count,
                    coin_amount, status, achieved_at, eligible_at, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                on conflict (account_id, reward_type, milestone_key) do nothing
                """,
                id,
                accountId,
                type,
                milestoneKey,
                requiredCount,
                currentCount,
                coinAmount,
                Timestamp.from(achievedAt),
                Timestamp.from(eligibleAt),
                Timestamp.from(now),
                Timestamp.from(now)
            )
        } catch (_: DataIntegrityViolationException) {
            0
        }
        if (inserted == 0) return null
        jdbc.update(
            """
            update reward_wallets
            set lifetime_earned = lifetime_earned + ?,
                updated_at = ?
            where account_id = ?
            """,
            coinAmount,
            Timestamp.from(now),
            accountId
        )
        insertLedger(accountId, id, "AWARD_PENDING", coinAmount, now)
        notificationPublisher.publish(
            NotificationCommand(
                accountId,
                null,
                NotificationType.REWARD_MILESTONE_ACHIEVED,
                NotificationTargetType.REWARD,
                id,
                id,
                rewardTitle(type, requiredCount, coinAmount)
            )
        )
        return AchievementRow(id, accountId, type, milestoneKey, requiredCount, currentCount, coinAmount, PENDING, achievedAt, eligibleAt, null)
    }

    private fun isClaimable(achievement: AchievementRow, now: Instant): Boolean {
        if (achievement.status != PENDING || achievement.eligibleAt.isAfter(now)) return false
        return when (achievement.type) {
            SIGNUP_BONUS, REEL_SCROLL -> true
            REEL_UPLOAD -> reelUploadCount(achievement.accountId, now.minus(properties.reelUploadClaimBuffer)) >= achievement.requiredCount
            FOLLOWER_MILESTONE -> followerCount(achievement.accountId, now.minus(properties.followerClaimBuffer)) >= achievement.requiredCount
            CONTENT_LIKE_MILESTONE -> contentLikeCount(achievement.accountId, now.minus(properties.likeClaimBuffer)) >= achievement.requiredCount
            STORY_LIKE_MILESTONE -> storyLikeCount(achievement.accountId, now.minus(properties.likeClaimBuffer)) >= achievement.requiredCount
            else -> false
        }
    }

    private fun response(achievement: AchievementRow, now: Instant) = RewardAchievementResponse(
        id = achievement.id,
        type = achievement.type,
        title = rewardTitle(achievement.type, achievement.requiredCount, achievement.coinAmount),
        milestoneKey = achievement.milestoneKey,
        currentCount = currentCount(achievement, now),
        requiredCount = achievement.requiredCount,
        coinAmount = achievement.coinAmount,
        status = if (achievement.status == PENDING && isClaimable(achievement, now)) "CLAIMABLE" else achievement.status,
        claimable = isClaimable(achievement, now),
        achievedAt = achievement.achievedAt,
        eligibleAt = achievement.eligibleAt,
        claimedAt = achievement.claimedAt
    )

    private fun currentCount(achievement: AchievementRow, now: Instant) = when (achievement.type) {
        SIGNUP_BONUS -> 1
        REEL_SCROLL -> reelScrollCount(achievement.accountId)
        REEL_UPLOAD -> reelUploadCount(achievement.accountId, null)
        FOLLOWER_MILESTONE -> followerCount(achievement.accountId, null)
        CONTENT_LIKE_MILESTONE -> contentLikeCount(achievement.accountId, null)
        STORY_LIKE_MILESTONE -> storyLikeCount(achievement.accountId, null)
        else -> achievement.currentCount
    }

    private fun progressItem(
        accountId: UUID,
        type: String,
        title: String,
        currentCount: Long,
        milestones: Map<Int, Int>
    ): RewardProgressItemResponse {
        val completed = jdbc.queryForObject(
            "select count(*) from reward_achievements where account_id = ? and reward_type = ?",
            Long::class.java,
            accountId,
            type
        ) ?: 0L
        val next = milestones.entries.firstOrNull { currentCount < it.key }
        return RewardProgressItemResponse(
            type = type,
            title = title,
            currentCount = currentCount,
            nextRequiredCount = next?.key?.toLong(),
            nextCoinAmount = next?.value?.toLong(),
            completedMilestones = completed.toInt()
        )
    }

    private fun scrollMilestones(accountId: UUID): Map<Int, Int> {
        val count = reelScrollCount(accountId)
        val next = (((count / properties.reelScrollUnit) + 1) * properties.reelScrollUnit).toInt()
        return mapOf(next to properties.reelScrollCoins)
    }

    private fun reelUploadMilestones(accountId: UUID): Map<Int, Int> {
        val count = reelUploadCount(accountId, null)
        val next = (((count / properties.reelUploadUnit) + 1) * properties.reelUploadUnit).toInt()
        return mapOf(next to properties.reelUploadCoins)
    }

    private fun achievement(accountId: UUID, achievementId: UUID): AchievementRow? =
        jdbc.query(
            "select * from reward_achievements where account_id = ? and id = ?",
            achievementMapper,
            accountId,
            achievementId
        ).firstOrNull()

    private fun achievementRows(accountId: UUID, cursor: AchievementCursor?, limit: Int): List<AchievementRow> =
        if (cursor == null) {
            jdbc.query(
                """
                select * from reward_achievements
                where account_id = ?
                order by achieved_at desc, id desc
                limit ?
                """,
                achievementMapper,
                accountId,
                limit
            )
        } else {
            jdbc.query(
                """
                select * from reward_achievements
                where account_id = ?
                  and (achieved_at < ? or (achieved_at = ? and id < ?))
                order by achieved_at desc, id desc
                limit ?
                """,
                achievementMapper,
                accountId,
                Timestamp.from(cursor.achievedAt),
                Timestamp.from(cursor.achievedAt),
                cursor.id,
                limit
            )
        }

    private fun ensureWallet(accountId: UUID) {
        jdbc.update(
            """
            insert into reward_wallets(account_id, available_balance, lifetime_earned, lifetime_claimed, created_at, updated_at)
            values (?, 0, 0, 0, ?, ?)
            on conflict (account_id) do nothing
            """,
            accountId,
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now())
        )
    }

    private fun ensureWalletReadOnly(accountId: UUID) {
        require(hasAccount(accountId)) { "Account was not found" }
    }

    private fun wallet(accountId: UUID): WalletRow =
        jdbc.query(
            "select * from reward_wallets where account_id = ?",
            walletMapper,
            accountId
        ).firstOrNull() ?: WalletRow(accountId, 0, 0, 0)

    private fun insertLedger(accountId: UUID, achievementId: UUID, type: String, amount: Long, now: Instant) {
        jdbc.update(
            """
            insert into reward_ledger(id, account_id, achievement_id, transaction_type, coin_amount, created_at)
            values (?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            accountId,
            achievementId,
            type,
            amount,
            Timestamp.from(now)
        )
    }

    private fun hasAccount(accountId: UUID): Boolean =
        count("select count(*) from identity_accounts where id = ?", accountId) > 0

    private fun reelScrollCount(accountId: UUID): Long =
        count("select count(*) from reward_reel_scrolls where account_id = ?", accountId)

    private fun reelUploadCount(accountId: UUID, oldEnoughAt: Instant?): Long =
        if (oldEnoughAt == null) {
            count(
                """
                select count(*) from content_items
                where owner_account_id = ?
                  and content_kind = 'REEL'
                  and status = 'PUBLISHED'
                """,
                accountId
            )
        } else {
            count(
                """
                select count(*) from content_items
                where owner_account_id = ?
                  and content_kind = 'REEL'
                  and status = 'PUBLISHED'
                  and published_at <= ?
                """,
                accountId,
                Timestamp.from(oldEnoughAt)
            )
        }

    private fun followerCount(accountId: UUID, followedBefore: Instant?): Long =
        if (followedBefore == null) {
            count(
                """
                select count(*) from identity_follows
                where followed_account_id = ?
                  and status = 'FOLLOWING'
                """,
                accountId
            )
        } else {
            count(
                """
                select count(*) from identity_follows
                where followed_account_id = ?
                  and status = 'FOLLOWING'
                  and followed_at <= ?
                """,
                accountId,
                Timestamp.from(followedBefore)
            )
        }

    private fun contentLikeCount(accountId: UUID, likedBefore: Instant?): Long =
        if (likedBefore == null) {
            count(
                """
                select count(*) from content_likes like_event
                join content_items content on content.id = like_event.content_id
                where content.owner_account_id = ?
                  and like_event.content_id is not null
                """,
                accountId
            )
        } else {
            count(
                """
                select count(*) from content_likes like_event
                join content_items content on content.id = like_event.content_id
                where content.owner_account_id = ?
                  and like_event.content_id is not null
                  and like_event.created_at <= ?
                """,
                accountId,
                Timestamp.from(likedBefore)
            )
        }

    private fun storyLikeCount(accountId: UUID, likedBefore: Instant?): Long =
        if (likedBefore == null) {
            count(
                """
                select count(*) from content_likes like_event
                join content_stories story on story.id = like_event.story_id
                where story.owner_account_id = ?
                  and like_event.story_id is not null
                """,
                accountId
            )
        } else {
            count(
                """
                select count(*) from content_likes like_event
                join content_stories story on story.id = like_event.story_id
                where story.owner_account_id = ?
                  and like_event.story_id is not null
                  and like_event.created_at <= ?
                """,
                accountId,
                Timestamp.from(likedBefore)
            )
        }

    private fun count(sql: String, vararg args: Any?): Long =
        jdbc.queryForObject(sql, Long::class.java, *args) ?: 0L

    private fun rewardTitle(type: String, requiredCount: Long, coinAmount: Long) = when (type) {
        SIGNUP_BONUS -> "Sign-up bonus earned: $coinAmount coins"
        REEL_SCROLL -> "$requiredCount reels scrolled: $coinAmount coin earned"
        REEL_UPLOAD -> "$requiredCount reels uploaded: $coinAmount coin earned"
        FOLLOWER_MILESTONE -> "$requiredCount followers reached: $coinAmount coins earned"
        CONTENT_LIKE_MILESTONE -> "$requiredCount post/reel likes reached: $coinAmount coins earned"
        STORY_LIKE_MILESTONE -> "$requiredCount story likes reached: $coinAmount coins earned"
        else -> "$coinAmount coins earned"
    }

    private data class WalletRow(
        val accountId: UUID,
        val balance: Long,
        val lifetimeEarned: Long,
        val lifetimeClaimed: Long
    )

    private data class AchievementRow(
        val id: UUID,
        val accountId: UUID,
        val type: String,
        val milestoneKey: String,
        val requiredCount: Long,
        val currentCount: Long,
        val coinAmount: Long,
        val status: String,
        val achievedAt: Instant,
        val eligibleAt: Instant,
        val claimedAt: Instant?
    )

    private data class AchievementCursor(val achievedAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$achievedAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(row: AchievementRow) = AchievementCursor(row.achievedAt, row.id)
            fun decode(value: String): AchievementCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid reward cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid reward cursor" }
                return AchievementCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]))
            }
        }
    }

    private companion object {
        const val SIGNUP_BONUS = "SIGNUP_BONUS"
        const val REEL_SCROLL = "REEL_SCROLL"
        const val REEL_UPLOAD = "REEL_UPLOAD"
        const val FOLLOWER_MILESTONE = "FOLLOWER_MILESTONE"
        const val CONTENT_LIKE_MILESTONE = "CONTENT_LIKE_MILESTONE"
        const val STORY_LIKE_MILESTONE = "STORY_LIKE_MILESTONE"
        const val PENDING = "PENDING"
        const val CLAIMED = "CLAIMED"

        val walletMapper = RowMapper { rs: ResultSet, _: Int ->
            WalletRow(
                rs.getObject("account_id", UUID::class.java),
                rs.getLong("available_balance"),
                rs.getLong("lifetime_earned"),
                rs.getLong("lifetime_claimed")
            )
        }

        val achievementMapper = RowMapper { rs: ResultSet, _: Int ->
            AchievementRow(
                id = rs.getObject("id", UUID::class.java),
                accountId = rs.getObject("account_id", UUID::class.java),
                type = rs.getString("reward_type"),
                milestoneKey = rs.getString("milestone_key"),
                requiredCount = rs.getLong("required_count"),
                currentCount = rs.getLong("current_count"),
                coinAmount = rs.getLong("coin_amount"),
                status = rs.getString("status"),
                achievedAt = rs.getTimestamp("achieved_at").toInstant(),
                eligibleAt = rs.getTimestamp("eligible_at").toInstant(),
                claimedAt = rs.getTimestamp("claimed_at")?.toInstant()
            )
        }
    }
}
