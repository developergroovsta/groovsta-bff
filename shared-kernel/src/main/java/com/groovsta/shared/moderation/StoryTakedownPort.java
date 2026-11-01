package com.groovsta.shared.moderation;

import java.util.UUID;

public interface StoryTakedownPort {
    void takeDownStory(UUID storyId, String reason);
}
