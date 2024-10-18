package com.rte_france.antares.datamanager_back.security.config.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@RequiredArgsConstructor
public class UrlAuthorizationConfigurerCustomizer implements Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> {

    private final RestSecurityProperties securityProperties;
    List<String> apiMatchers = new ArrayList<>(List.of("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**"));

    @Override
    public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorizationManagerRequestMatcherRegistry) {
        setPermitAllApiMatcher(authorizationManagerRequestMatcherRegistry);
        setAuthenticatedApiMatcher(authorizationManagerRequestMatcherRegistry);
    }

    private void setPermitAllApiMatcher(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorizationManagerRequestMatcherRegistry) {
        if (isNotEmpty(securityProperties.getPermitAll()) && isNotEmpty(securityProperties.getPermitAll().getApiMatcher())) {
            apiMatchers.addAll(securityProperties.getPermitAll().getApiMatcher());
        }
        for (String apiMatcher : apiMatchers) {
            authorizationManagerRequestMatcherRegistry.requestMatchers(apiMatcher).permitAll();
        }
    }

    private void setAuthenticatedApiMatcher(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorizationManagerRequestMatcherRegistry) {
        if (isNotEmpty(securityProperties.getAuthenticated()) && isNotEmpty(securityProperties.getAuthenticated().getApiMatcher())) {
            for (String apiMatcher : securityProperties.getAuthenticated().getApiMatcher()) {
                authorizationManagerRequestMatcherRegistry.requestMatchers(apiMatcher).authenticated();
            }
        } else
            authorizationManagerRequestMatcherRegistry.requestMatchers("/**").authenticated();
    }
}
