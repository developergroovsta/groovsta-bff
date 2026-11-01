package com.groovsta.shared.storage;

import java.time.Duration;
import java.util.Optional;

public interface ObjectStorageService {
    PresignedUpload createUpload(String objectKey, String contentType, Duration validity);
    String createDownloadUrl(String objectKey, Duration validity);
    Optional<StoredObjectMetadata> metadata(String objectKey);
    boolean exists(String objectKey);
    void delete(String objectKey);
}
