package com.groovsta.rewards.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

data class RewardSummaryResponse(
    val walletBalance: Long,
    val lifetimeEarned: Long,
    val lifetimeClaimed: Long,
    val pendingCoins: Long,
    val claimableCoins: Long,
    val achievements: List<RewardAchievementResponse>
)

data class RewardAchievementPageResponse(
    val items: List<RewardAchievementResponse>,
    val nextCursor: String?
)

data class RewardAchievementResponse(
    val id: UUID,
    val type: String,
    val title: String,
    val milestoneKey: String,
    val currentCount: Long,
    val requiredCount: Long,
    val coinAmount: Long,
    val status: String,
    val claimable: Boolean,
    val achievedAt: Instant,
    val eligibleAt: Instant,
    val claimedAt: Instant?
)

data class ClaimRewardRequest(
    val achievementId: UUID? = null
)

data class ClaimRewardResponse(
    val claimedCoins: Long,
    val walletBalance: Long,
    val claimedAchievements: List<RewardAchievementResponse>
)

data class RecordReelScrollRequest(
    val contentId: UUID
)

data class RecordReelScrollResponse(
    val contentId: UUID,
    val uniqueReelsScrolled: Long,
    val nextMilestoneAt: Long,
    val newlyEarnedAchievement: RewardAchievementResponse?
)

data class RewardProgressResponse(
    val items: List<RewardProgressItemResponse>
)

data class RewardProgressItemResponse(
    val type: String,
    val title: String,
    val currentCount: Long,
    val nextRequiredCount: Long?,
    val nextCoinAmount: Long?,
    val completedMilestones: Int
)
