package com.rte_france.antares.datamanager_back.service.user;

import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class UserService {

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
            log.info("User authenticated: {}", user.getFirstName());
            return user;
        }
        else {
            log.info("There is no authenticated user");
            return UserInfoDto.builder()
                    .nni("unknown_user")
                    .build();
        }
    }
}