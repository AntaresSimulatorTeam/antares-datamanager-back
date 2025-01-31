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

import java.util.List;

public class OpenApiConfig {

    private static final String OAUTH_CLIENT_CREDENTIALS = "oauth_client_credentials";
    private static final String TOKEN_BEARER_KEY = "token_bearer";

    @Value("${spring.security.oauth2.client.provider.token-uri}")
    private String tokenUrl;

    @Value("${project.version:1.0.0}")
    private String appVersion;

    @Value("${project.title:Title of the application.}")
    private String appTitle;

    @Value("${project.description:Description of the application.}")
    private String appDescription;

    @Value("${swagger.contact.team.name:Team Name}")
    private String teamName;

    @Value("${swagger.contact.team.email:email@rte.com}")
    private String teamEmail;

    /**
     * CORS configuration.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins("*");
            }
        };
    }

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme oauthClientCredentials = getOAuthClientCredentialsSecurityScheme();
        SecurityScheme tokenBearer = getTokenBearerSecurityScheme();

        Components components = new Components()
                .addSecuritySchemes(OAUTH_CLIENT_CREDENTIALS, oauthClientCredentials)
                .addSecuritySchemes(TOKEN_BEARER_KEY, tokenBearer);

        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(new Server().url("..").description("Relative API server")))
                .components(components)
                .security(List.of(
                        new SecurityRequirement().addList(OAUTH_CLIENT_CREDENTIALS),
                        new SecurityRequirement().addList(TOKEN_BEARER_KEY)
                ));
    }

    private Info apiInfo() {
        return new Info()
                .title(appTitle)
                .description(appDescription)
                .version(appVersion)
                .contact(new Contact()
                        .name(teamName)
                        .email(teamEmail)
                );
    }

    /**
     * Security Scheme for Bearer Token (Opaque Token)
     */
    private SecurityScheme getTokenBearerSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")  // Keep bearer scheme for token submission
                .description("OAuth2 Opaque Token authentication");
    }

    /**
     * OAuth2 Security Scheme for Client Credentials (Opaque Token Flow)
     */
    private SecurityScheme getOAuthClientCredentialsSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth2 Client Credentials flow for Opaque Token")
                .flows(new OAuthFlows()
                        .clientCredentials(new OAuthFlow()
                                .tokenUrl(tokenUrl)
                                .scopes(new Scopes())
                        ));
    }
}

