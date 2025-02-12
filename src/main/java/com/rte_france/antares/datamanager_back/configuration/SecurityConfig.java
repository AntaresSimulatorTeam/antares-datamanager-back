package com.rte_france.antares.datamanager_back.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.introspection-uri}")
    private String introspectionUri;

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.client-id}")
    private String introspectionClientId;

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.client-secret}")
    private String introspectionClientSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Allow Swagger UI & API docs
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        //uncommented the following line to disable security
                        //.requestMatchers("/v1/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaque -> opaque
                                .introspectionUri(introspectionUri)
                                .introspectionClientCredentials(introspectionClientId, introspectionClientSecret)
                        )
                );
        return http.build();
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
