package com.rte_france.antares.datamanager_back.service;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private BearerTokenAuthentication tokenAuth;

    @Mock
    private OAuth2AuthenticatedPrincipal principal;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void getCurrentUserDetails_returnsUserInfoDtoWhenAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(tokenAuth);
        when(tokenAuth.getPrincipal()).thenReturn(principal);
        when(principal.getAttributes()).thenReturn(Map.of(
                "username", "testUser",
                "family_name", "Test",
                "given_name", "User"
        ));

        UserInfoDto userInfo = userService.getCurrentUserDetails();

        assertNotNull(userInfo);
        assertEquals("testUser", userInfo.getNni());
        assertEquals("Test", userInfo.getFirstName());
        assertEquals("User", userInfo.getLastName());
    }
    @Test
    void getCurrentUserDetails_returnsUnknownUserWhenNotAuthenticated() {
        when(securityContext.getAuthentication()).thenReturn(authentication);

        UserInfoDto userInfo = userService.getCurrentUserDetails();

        assertNotNull(userInfo);
        assertEquals("unknown_user", userInfo.getNni());
    }
}