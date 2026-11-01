package com.groovsta.shared.profile;

public record ProfileContentStats(long postCount) {
    public static ProfileContentStats empty() {
        return new ProfileContentStats(0);
    }
}
