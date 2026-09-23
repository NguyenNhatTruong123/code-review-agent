package com.codereviewagent.api.service;

import com.codereviewagent.api.repository.UserRepository;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated principal to the application user ID. */
@Component
public class CurrentUser {
    private final UserRepository users;
    /** Creates a resolver backed by the application user repository.
     * @param users repository used to resolve the authenticated username
     */
    public CurrentUser(UserRepository users) { this.users = users; }
    /** Fails with 401 when the session principal no longer maps to a stored user.
     * @param principal authenticated session principal
     * @return application user ID
     * @throws ResponseStatusException with HTTP 401 when the user no longer exists
     */
    public String id(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")).id;
    }
}
