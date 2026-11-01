package com.groovsta.rewards.service

import com.groovsta.rewards.dto.ClaimRewardRequest
import com.groovsta.rewards.dto.ClaimRewardResponse
import com.groovsta.rewards.dto.RecordReelScrollRequest
import com.groovsta.rewards.dto.RecordReelScrollResponse
import com.groovsta.rewards.dto.RewardAchievementPageResponse
import com.groovsta.rewards.dto.RewardProgressResponse
import com.groovsta.rewards.dto.RewardSummaryResponse
import java.util.UUID

interface RewardService {
    fun summary(accountId: UUID): RewardSummaryResponse
    fun achievements(accountId: UUID, cursor: String?, limit: Int): RewardAchievementPageResponse
    fun progress(accountId: UUID): RewardProgressResponse
    fun claim(accountId: UUID, request: ClaimRewardRequest): ClaimRewardResponse
    fun recordReelScroll(accountId: UUID, request: RecordReelScrollRequest): RecordReelScrollResponse
}
