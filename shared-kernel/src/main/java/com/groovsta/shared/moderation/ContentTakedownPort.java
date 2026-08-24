package com.groovsta.shared.moderation;

import java.util.UUID;

public interface ContentTakedownPort {
    void takeDown(UUID contentId, String reason);
}
