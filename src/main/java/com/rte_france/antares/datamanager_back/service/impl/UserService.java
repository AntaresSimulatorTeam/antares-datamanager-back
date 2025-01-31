package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Component;

import java.util.Map;
@Slf4j
@Configuration
@Component
public class UserService {

    @Bean
    public UserInfoDto getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof BearerTokenAuthentication tokenAuth) {
            OAuth2AuthenticatedPrincipal principal = (OAuth2AuthenticatedPrincipal) tokenAuth.getPrincipal();
            Map<String, Object> attributes = principal.getAttributes();

            var user = UserInfoDto.builder()
                    .nni((String) attributes.get("username"))
                    .firstName((String) attributes.get("family_name"))
                    .lastName((String) attributes.get("given_name"))
                    .build();
            log.debug("User authenticated: {}", user.getFirstName());
            return user;
        }
        else {
            return null;
        }
    }
}