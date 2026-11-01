package com.groovsta.shared.reward;

import java.time.Instant;
import java.util.UUID;

public interface RewardEventPublisher {
    void signupCompleted(UUID accountId);

    void reelPublished(UUID ownerAccountId, UUID contentId, Instant publishedAt);

    void followEstablished(UUID followedAccountId, UUID followerAccountId, UUID followId, Instant followedAt);

    void contentLiked(UUID ownerAccountId, UUID likerAccountId, UUID contentId, UUID likeId, Instant likedAt);

    void storyLiked(UUID ownerAccountId, UUID likerAccountId, UUID storyId, UUID likeId, Instant likedAt);
}
