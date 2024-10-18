package com.rte_france.antares.datamanager_back.security.context.impl;

import com.nimbusds.jose.shaded.gson.Gson;
import com.nimbusds.jose.shaded.gson.JsonArray;
import com.nimbusds.jose.shaded.gson.JsonElement;
import com.nimbusds.jose.shaded.gson.JsonObject;

import com.rte_france.antares.datamanager_back.security.context.AuthenticatedUser;
import com.rte_france.antares.datamanager_back.security.context.UserContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextHelperImpl implements UserContextHelper {

    private static final String UNKNOWN_ATTRIBUTE = "unknown";
    private static final String PLASMA_REALM_ACCESS = "realm_access";
    private static final String PLASMA_ROLES = "roles";
    private static final String PLASMA_PREFERRED_USER_NAME = "preferred_username";
    private static final String PLASMA_PREFERRED_FIRST_NAME = "given_name";
    private static final String PLASMA_PREFERRED_LAST_NAME = "family_name";

    public AuthenticatedUser buildAuthenticatedUser() {
        try {
            final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return buildAuthenticatedUser((JwtAuthenticationToken) authentication);
        } catch (ClassCastException exception)
        {
            return null;
        }
    }

    public AuthenticatedUser buildAuthenticatedUser(final JwtAuthenticationToken authentication) {
        final Map<String, Object> attributes = authentication.getTokenAttributes();

        final Set<String> roles = getRealmAccessRoles(attributes);

        final Optional<String> preferredUsername = Optional.ofNullable((String) attributes.get(PLASMA_PREFERRED_USER_NAME));
        return AuthenticatedUser.builder()
                .id(authentication.getName())
                .nni(preferredUsername.orElse(UNKNOWN_ATTRIBUTE))
                .preferredUserName(preferredUsername.orElse(UNKNOWN_ATTRIBUTE))
                .firstName((String) attributes.getOrDefault(PLASMA_PREFERRED_FIRST_NAME, UNKNOWN_ATTRIBUTE))
                .lastName((String) attributes.getOrDefault(PLASMA_PREFERRED_LAST_NAME, UNKNOWN_ATTRIBUTE))
                .userRoles(roles)
                .build();
    }


    private Set<String> getRealmAccessRoles(final Map<String, Object> attributes) {

        // Convert LinkedTreeMap to JsonObject
        Gson gson = new Gson();
        JsonElement jsonElement = gson.toJsonTree(attributes.get(PLASMA_REALM_ACCESS));
        JsonObject realmAccess = jsonElement.getAsJsonObject();
        JsonElement roles  = realmAccess.get(PLASMA_ROLES);
        JsonArray jsonArray = roles.getAsJsonArray();
        Set<String> rolesSet = new HashSet<>();
        jsonArray.forEach(role -> rolesSet.add(role.getAsString()));
        return rolesSet;
    }
}