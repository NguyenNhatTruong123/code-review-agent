package com.codereviewagent.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codereviewagent.api.model.UserAccount;
import com.codereviewagent.api.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

class CurrentUserTest {
    @Test
    void resolvesIdFromAuthenticatedPrincipal() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("alice"))
                .thenReturn(Optional.of(new UserAccount("id-1", "alice", "hash")));
        assertEquals("id-1", new CurrentUser(users).id(() -> "alice"));
    }

    @Test
    void rejectsDeletedAccount() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> new CurrentUser(users).id(() -> "alice"));
    }
}
