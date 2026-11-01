package com.groovsta.feed.config

import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FeedOpenApiConfiguration {

    @Bean
    fun optionalFeedAuthentication(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        optionalAuthenticationPaths.forEach { path ->
            openApi.paths[path]?.get?.security = listOf(
                SecurityRequirement(),
                SecurityRequirement().addList(OpenApiConfiguration.BEARER_AUTH)
            )
        }
    }

    private companion object {
        val optionalAuthenticationPaths = setOf(
            "/api/v1/feeds/home",
            "/api/v1/profiles/{handle}/posts",
            "/api/v1/profiles/{handle}/reels"
        )
    }
}
