package com.groovsta.rewards.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "groovsta.rewards")
data class RewardProperties(
    val signupCoins: Int = 5,
    val reelScrollUnit: Int = 100,
    val reelScrollCoins: Int = 1,
    val reelUploadUnit: Int = 5,
    val reelUploadCoins: Int = 1,
    val reelUploadClaimBuffer: Duration = Duration.ofDays(7),
    val likeClaimBuffer: Duration = Duration.ofDays(7),
    val followerClaimBuffer: Duration = Duration.ofDays(7),
    val followerMilestones: Map<Int, Int> = linkedMapOf(1_000 to 50, 5_000 to 100, 10_000 to 500),
    val likeMilestones: Map<Int, Int> = linkedMapOf(200 to 1, 500 to 2),
    val viewMilestones: Map<Int, Int> = linkedMapOf(1_000 to 5, 5_000 to 10, 10_000 to 20)
)
