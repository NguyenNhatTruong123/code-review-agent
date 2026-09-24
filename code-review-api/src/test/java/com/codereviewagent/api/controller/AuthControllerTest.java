package com.codereviewagent.api.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codereviewagent.api.repository.UserRepository;
import com.codereviewagent.api.service.RuleService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

class AuthControllerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthenticationManager auth = mock(AuthenticationManager.class);
    private final HttpSessionSecurityContextRepository contexts =
            mock(HttpSessionSecurityContextRepository.class);
    private final RuleService rules = mock(RuleService.class);
    private final AuthController controller =
            new AuthController(users, encoder, auth, contexts, rules);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersNormalizedAccountAndStarterRules() {
        when(encoder.encode("long-password")).thenReturn("hash");
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(
                "alice",
                controller
                        .register(new AuthController.Credentials("Alice", "long-password"))
                        .get("username"));
        verify(users)
                .save(
                        argThat(
                                user ->
                                        user.username.equals("alice")
                                                && user.passwordHash.equals("hash")));
        verify(rules).createDefaults(any());
    }

    @Test
    void rejectsDuplicateOrMalformedUsername() {
        when(users.existsByUsername("alice")).thenReturn(true);
        assertThrows(
                ResponseStatusException.class,
                () ->
                        controller.register(
                                new AuthController.Credentials("alice", "long-password")));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        controller.register(
                                new AuthController.Credentials("bad name", "long-password")));
    }

    @Test
    void loginSavesAuthenticatedSession() {
        var authenticated =
                new UsernamePasswordAuthenticationToken(
                        "alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(auth.authenticate(any())).thenReturn(authenticated);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        assertEquals(
                "alice",
                controller
                        .login(
                                new AuthController.Credentials("ALICE", "long-password"),
                                request,
                                response)
                        .get("username"));
        verify(request).changeSessionId();
        verify(contexts).saveContext(any(), eq(request), eq(response));
    }
}
