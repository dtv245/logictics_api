package com.company.logicstic.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at {@code /v3/api-docs} and rendered by Swagger UI at {@code
 * /swagger-ui.html}.
 *
 * <p>Both are enabled per profile and switched off in production (see {@code
 * application-prod.yml}). The Angular portals generate their TypeScript client from this document
 * ({@code docs/docs/api/overview.md} §API Client Generation), so it is a build output, not only a
 * browsing aid.
 *
 * <p>Two things every operation inherits from here: the {@code bearerAuth} scheme, because all of
 * {@code /api/**} is an OAuth2 resource server, and the {@code X-Tenant} header, because a request
 * that resolves to no tenant is rejected rather than served from a default database ({@code
 * docs/docs/architecture/multi-tenancy.md}).
 */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_AUTH = "bearerAuth";

  private final String applicationName;

  public OpenApiConfig(@Value("${spring.application.name}") String applicationName) {
    this.applicationName = applicationName;
  }

  @Bean
  public OpenAPI logisticsApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "Access token issued by the Identity Server. The 'tenant' claim is"
                                + " mandatory.")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
        .servers(
            List.of(
                new Server().url("/").description("This instance"),
                new Server().url("https://api.yourdomain.com").description("Production")))
        .info(
            new Info()
                .title("LogisticsX API")
                .description(
                    """
                    Multi-tenant Transportation Management System.

                    Every response is wrapped in an `ApiResponse` envelope \
                    (`success`, `code`, `message`, `data`, `errors`, `meta`); list endpoints wrap a \
                    `PagedResponse`. Pagination is 1-based (`page`, `pageSize`).

                    Tenant scope is resolved per request from the MCP API key, the `X-Tenant` \
                    header, or the JWT `tenant` claim, in that order. A request that resolves to no \
                    tenant is rejected.

                    Application: %s
                    """
                        .formatted(applicationName))
                .version("v1.0")
                .contact(new Contact().name("DANG THE VU"))
                .license(new License().name("MIT")))
        .externalDocs(
            new ExternalDocumentation()
                .description("Architecture, business spec and API reference")
                .url("https://github.com/suxrobgm/logistics-app/tree/main/docs"));
  }
}
