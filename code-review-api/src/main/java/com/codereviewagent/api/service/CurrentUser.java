package com.codereviewagent.api.service;

import com.codereviewagent.api.repository.UserRepository;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {
    private final UserRepository users;
    public CurrentUser(UserRepository users) { this.users = users; }
    public String id(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")).id;
    }
}
