package com.rte_france.antares.datamanager_back.security.converter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public final class CustomKeyCloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String AUTHORITY_PREFIX = "ROLE_";
    private static final String CLAIM_ROLES = "roles";
    private static final String REALM_ACCESS = "realm_access";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        log.debug("Jwt {}", jwt.getClaims().toString());
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(jwt) && ObjectUtils.isNotEmpty(jwt.getClaims().get(REALM_ACCESS))) {
            final Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get(REALM_ACCESS);
            if (ObjectUtils.isNotEmpty(realmAccess) && ObjectUtils.isNotEmpty(realmAccess.get(CLAIM_ROLES))) {
                grantedAuthorities.addAll(((List<String>) realmAccess.get(CLAIM_ROLES)).stream()
                        .map(roleName -> AUTHORITY_PREFIX + roleName) // prefix to map to a Spring Security "role"
                        .map(SimpleGrantedAuthority::new)
                        .toList());

            }
        }
        log.debug("Roles from JWT : {}", grantedAuthorities);
        return grantedAuthorities;
    }
}
