package com.groovsta.shared.profile;

import java.util.UUID;

public interface ProfileContentStatsProvider {
    ProfileContentStats statsFor(UUID accountId);
}
