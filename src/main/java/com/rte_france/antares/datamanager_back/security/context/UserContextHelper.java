package com.rte_france.antares.datamanager_back.security.context;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public interface UserContextHelper {

    /**
     * Build authenticated user from token in context
     *
     * @return AuthenticatedUser
     */
    AuthenticatedUser buildAuthenticatedUser();

    /**
     * Build authenticated user from given token
     *
     * @param authentication JwtAuthenticationToken of user to build
     * @return AuthenticatedUser
     */
    AuthenticatedUser buildAuthenticatedUser(final JwtAuthenticationToken authentication);
}
