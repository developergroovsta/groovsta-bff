package com.groovsta.identity.web

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme

@OpenAPIDefinition(
    info = Info(
        title = "Groovsta Identity API",
        version = "v1",
        description = "Authentication, verification, profile, and social graph APIs."
    )
)
@SecurityScheme(
    name = OpenApiConfiguration.BEARER_AUTH,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    `in` = SecuritySchemeIn.HEADER
)
class OpenApiConfiguration {
    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
