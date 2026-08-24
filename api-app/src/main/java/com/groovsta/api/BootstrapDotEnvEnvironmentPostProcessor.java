package com.groovsta.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BootstrapDotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final Logger log = LoggerFactory.getLogger(BootstrapDotEnvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (Path candidate : candidates()) {
            if (Files.isReadable(candidate)) {
                loadDotEnv(environment, candidate);
                log.info("Loaded runtime environment from {}", candidate.toAbsolutePath().normalize());
                return;
            }
        }
        log.warn(
                "No readable .env file found from user.dir={}. Checked {}",
                System.getProperty("user.dir"),
                candidates().stream().map(path -> path.toAbsolutePath().normalize().toString()).toList()
        );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void loadDotEnv(ConfigurableEnvironment environment, Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, Object> values = new LinkedHashMap<>();
            reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .filter(line -> line.contains("="))
                    .forEach(line -> {
                        int separator = line.indexOf('=');
                        String key = line.substring(0, separator).trim();
                        String value = line.substring(separator + 1);
                        if (!key.isEmpty() && !environment.containsProperty(key)) {
                            values.put(key, value);
                        }
                    });
            if (!values.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource("groovstaDotEnv", values));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read environment file " + path, exception);
        }
    }

    private List<Path> candidates() {
        String userDir = System.getProperty("user.dir");
        Set<Path> paths = new LinkedHashSet<>();
        configuredPath("groovsta.env.file").ifPresent(paths::add);
        configuredPath("GROOVSTA_ENV_FILE").ifPresent(paths::add);
        paths.add(Path.of(userDir, ".env"));
        paths.add(Path.of(userDir, "..", ".env"));
        paths.add(Path.of(userDir, "..", "..", ".env"));
        paths.add(Path.of(userDir, "groovsta-bff", ".env"));
        paths.add(Path.of(userDir, "..", "groovsta-bff", ".env"));
        return paths.stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    private java.util.Optional<Path> configuredPath(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank()
                ? java.util.Optional.empty()
                : java.util.Optional.of(Path.of(value.trim()));
    }
}
