package com.groovsta.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("groovsta.storage.s3")
public class S3StorageProperties {
    private String bucket = "groovsta-local";
    private String region = "ap-south-1";
    private URI endpoint;
    private boolean pathStyleAccess = false;
    private String imagePrefix = "images";
    private String videoPrefix = "videos";
    private Duration uploadUrlTtl = Duration.ofMinutes(15);
    private Duration downloadUrlTtl = Duration.ofHours(1);

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public URI getEndpoint() { return endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public boolean isPathStyleAccess() { return pathStyleAccess; }
    public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }
    public String getImagePrefix() { return imagePrefix; }
    public void setImagePrefix(String imagePrefix) { this.imagePrefix = imagePrefix; }
    public String getVideoPrefix() { return videoPrefix; }
    public void setVideoPrefix(String videoPrefix) { this.videoPrefix = videoPrefix; }
    public Duration getUploadUrlTtl() { return uploadUrlTtl; }
    public void setUploadUrlTtl(Duration uploadUrlTtl) { this.uploadUrlTtl = uploadUrlTtl; }
    public Duration getDownloadUrlTtl() { return downloadUrlTtl; }
    public void setDownloadUrlTtl(Duration downloadUrlTtl) { this.downloadUrlTtl = downloadUrlTtl; }
}
