package com.rte_france.antares.datamanager_back.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String TOKEN_BEARER_KEY = "token_bearer";

    @Value("${project.version:1.0.0}")
    private String appVersion;

    @Value("${project.title:Pegase back.}")
    private String appTitle;

    @Value("${project.description:This microservice is the Antares pegase-back engine..}")
    private String appDescription;

    @Value("${swagger.contact.team.name:Antares pegase-back team}")
    private String teamName;

    @Value("${swagger.contact.team.email:email@rte.com}")
    private String teamEmail;

    /**
     * CORS configuration.
     */


    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme tokenBearer = getTokenBearerSecurityScheme();

        Components components = new Components()
                .addSecuritySchemes(TOKEN_BEARER_KEY, tokenBearer);

        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(new Server().url("..").description("Relative API server")))
                .components(components)
                .security(List.of(
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
}

