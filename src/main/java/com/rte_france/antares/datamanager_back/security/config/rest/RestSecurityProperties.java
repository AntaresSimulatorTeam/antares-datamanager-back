package com.rte_france.antares.datamanager_back.security.config.rest;



import com.rte_france.antares.datamanager_back.security.utils.rest.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rest.security")
public class RestSecurityProperties {

    private Cors cors;
    private PermitAll permitAll;
    private Authenticated authenticated;


    public CorsConfiguration getCorsConfiguration() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(Optional.ofNullable(cors.getAllowedOrigins()).orElse(Collections.singletonList("*")));
        corsConfiguration.setAllowedMethods(Optional.ofNullable(cors.getAllowedMethods()).orElse(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS")));
        corsConfiguration.setAllowedHeaders(Optional.ofNullable(cors.getAllowedHeaders()).orElse(Collections.singletonList("*")));
        corsConfiguration.setExposedHeaders(Optional.ofNullable(cors.getExposedHeaders()).orElse(Collections.emptyList()));
        corsConfiguration.setAllowCredentials(Optional.ofNullable(cors.getAllowCredentials()).orElse(true));
        corsConfiguration.setMaxAge(Optional.ofNullable(cors.getMaxAge()).orElse(3600L));

        return corsConfiguration;
    }
}
