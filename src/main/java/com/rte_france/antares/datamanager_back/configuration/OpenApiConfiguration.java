package com.rte_france.antares.datamanager_back.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@AutoConfiguration
public class OpenApiConfiguration {

    private static final String KEYCLOAK_URL_SUFFIX = "/protocol/openid-connect";
    private static final String OAUTH_PASSWORD_FLOW_KEY = "oauth_password_flow";
    private static final String TOKEN_BEARER_KEY = "token_bearer";

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String keycloakServer;

    @Value("${rest.security.cors.allowed-origins}")
    private String allowedCorsOrigins;

    @Value("${project.version}")
    private String appVersion;

    @Value("${project.title}")
    private String appTitle;

    @Value("${project.description}")
    private String appDescription;

    @Value("${swagger.contact.team.name}")
    private String teamName;

    @Value("${swagger.contact.team.email}")
    private String teamEmail;

    /**
     * CORS configuration.
     *
     * @return configurer
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins(allowedCorsOrigins);
            }
        };
    }

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme plasmaOAuthPasswordFlow = getOAuthSecurityScheme();
        SecurityScheme plasmaTokenBearer = getTokenBearerSecurityScheme();

        Components components = new Components()
                .addSecuritySchemes(OAUTH_PASSWORD_FLOW_KEY, plasmaOAuthPasswordFlow)
                .addSecuritySchemes(TOKEN_BEARER_KEY, plasmaTokenBearer);

        List<SecurityRequirement> securityRequirements = Arrays.asList(
                new SecurityRequirement().addList(OAUTH_PASSWORD_FLOW_KEY),
                new SecurityRequirement().addList(TOKEN_BEARER_KEY));

        return new OpenAPI()
                .info(apiInfo())
                // temporary hack : use relative URL; to be improved especially by upgrading springdoc version !
                .servers(List.of(new Server().url("..").description("Relative API server")))
                .components(components)
                .security(securityRequirements);
    }

    private Info apiInfo() {
        return new Info()
                .title(appTitle)
                .description(appDescription)
                .version(appVersion)
                .contact(apiContact());
    }

    private Contact apiContact() {
        return new Contact()
                .name(teamName)
                .email(teamEmail);
    }

    private SecurityScheme getTokenBearerSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .description("pegase token bearer access")
                .in(SecurityScheme.In.HEADER)
                .bearerFormat("jwt")
                .scheme("bearer");
    }

    private SecurityScheme getOAuthSecurityScheme() {
        String baseUrl = keycloakServer + KEYCLOAK_URL_SUFFIX;

        return new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("Pegase OAuth2 password flow")
                .flows(new OAuthFlows()
                        .password(new OAuthFlow()
                                .authorizationUrl(baseUrl + "/auth")
                                .refreshUrl(baseUrl + "/token")
                                .tokenUrl(baseUrl + "/token")
                                .scopes(new Scopes())
                        ));
    }
}
