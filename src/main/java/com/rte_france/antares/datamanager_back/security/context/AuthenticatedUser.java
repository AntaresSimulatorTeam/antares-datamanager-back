package com.rte_france.antares.datamanager_back.security.context;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Value
@Builder
public class AuthenticatedUser {

    /**
     * Keycloak id
     */
    String id;
    String nni;
    String preferredUserName;
    String firstName;
    String lastName;
    Set<String> userRoles;
}