package com.groovsta.shared.moderation;

import java.util.UUID;

public interface ContentReportGateway {
    UUID report(UUID contentId, UUID reporterAccountId, ReportCategory category, String details);
    UUID reportStory(UUID storyId, UUID reporterAccountId, ReportCategory category, String details);
}
