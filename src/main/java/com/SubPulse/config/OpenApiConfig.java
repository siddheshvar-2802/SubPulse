package com.subpulse.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 * Adds a "Bearer Token" auth button to the Swagger UI for easy API testing.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "SubPulse API",
        version     = "1.0",
        description = "Smart SaaS & Subscription Renewal Risk Engine — REST API Documentation",
        contact     = @Contact(name = "SubPulse Team", email = "support@subpulse.io")
    )
)
@SecurityScheme(
    name   = "bearerAuth",
    type   = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {
}
