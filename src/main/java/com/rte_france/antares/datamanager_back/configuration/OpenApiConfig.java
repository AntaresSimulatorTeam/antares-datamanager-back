package com.rte_france.antares.datamanager_back.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Pegase Back")
                        .version("1.0")
                        .description("UI for PEGASE back-end")
                        .contact(new Contact().name("ANTARES TEAM").email("antares-pegase@rte-france.com")));
    }
}