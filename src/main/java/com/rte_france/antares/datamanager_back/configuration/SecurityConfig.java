package com.rte_france.antares.datamanager_back.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
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
                .securityMatcher("/**") // Appliquer la config à tout le projet
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                ).addFilterBefore((request, response, chain) -> {
                    System.out.println("Request to: " + request.getRequestId());
                    chain.doFilter(request, response);
                }, UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaque -> opaque
                                .introspectionUri(introspectionUri)
                                .introspectionClientCredentials(introspectionClientId, introspectionClientSecret)
                        )
                );

        return http.build();
    }
}

