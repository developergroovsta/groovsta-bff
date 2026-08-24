package com.groovsta.shared.firebase;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("groovsta.firebase")
public class FirebaseProperties {
    private boolean enabled;
    private String projectId;
    private String credentialsPath;
    private String credentialsBase64;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    public String getCredentialsBase64() {
        return credentialsBase64;
    }

    public void setCredentialsBase64(String credentialsBase64) {
        this.credentialsBase64 = credentialsBase64;
    }
}
