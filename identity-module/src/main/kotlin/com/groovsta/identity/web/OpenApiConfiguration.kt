package com.groovsta.identity.web

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun groovstaOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Groovsta API")
                .version("v1")
                .description(
                    "Authentication, profiles, image posts, reels, feeds, chat, engagement, and moderation APIs."
                )
        )
        .components(
            Components().addSecuritySchemes(
                BEARER_AUTH,
                SecurityScheme()
                    .name(BEARER_AUTH)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("Opaque access token")
                    .description("Paste session.accessToken only. Swagger adds the Bearer prefix automatically.")
            )
        )

    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
