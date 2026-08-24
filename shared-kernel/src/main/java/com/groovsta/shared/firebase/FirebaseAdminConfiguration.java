package com.groovsta.shared.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
@ConditionalOnProperty(prefix = "groovsta.firebase", name = "enabled", havingValue = "true")
public class FirebaseAdminConfiguration {
    private static final String APP_NAME = "groovsta";

    @Bean(destroyMethod = "delete")
    FirebaseApp firebaseApp(FirebaseProperties properties) throws IOException {
        var options = FirebaseOptions.builder()
                .setCredentials(credentials(properties));
        var projectId = trimmed(properties.getProjectId());
        if (projectId != null) options.setProjectId(projectId);

        return FirebaseApp.getApps().stream()
                .filter(app -> APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options.build(), APP_NAME));
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private GoogleCredentials credentials(FirebaseProperties properties) throws IOException {
        var encoded = trimmed(properties.getCredentialsBase64());
        var path = trimmed(properties.getCredentialsPath());
        if (encoded != null && path != null) {
            throw new IllegalArgumentException(
                    "Configure only one of groovsta.firebase.credentials-base64 or credentials-path"
            );
        }
        if (encoded != null) {
            try (var stream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded))) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        if (path != null) {
            try (var stream = Files.newInputStream(Path.of(path))) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }

    private String trimmed(String value) {
        if (value == null) return null;
        var result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
