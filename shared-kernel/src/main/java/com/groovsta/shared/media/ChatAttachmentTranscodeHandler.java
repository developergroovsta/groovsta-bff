package com.groovsta.shared.media;

import java.util.UUID;

public interface ChatAttachmentTranscodeHandler {
    void completeChatAttachment(
            UUID attachmentId,
            String mediaObjectKey,
            String thumbnailObjectKey,
            int durationSeconds,
            Integer width,
            Integer height
    );

    void failChatAttachment(UUID attachmentId, String reason);
}
