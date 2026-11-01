package com.groovsta.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok", "service", "groovsta-api");
    }
}
