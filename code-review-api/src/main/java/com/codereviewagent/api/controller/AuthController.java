package com.codereviewagent.api.controller;

import com.codereviewagent.api.model.UserAccount;
import com.codereviewagent.api.repository.UserRepository;
import com.codereviewagent.api.service.RuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record Credentials(@NotBlank @Size(min = 3, max = 80) String username,
                              @NotBlank @Size(min = 12, max = 128) String password) {}
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository contextRepository;
    private final RuleService rules;

    public AuthController(UserRepository users, PasswordEncoder encoder, AuthenticationManager authenticationManager,
                          HttpSessionSecurityContextRepository contextRepository, RuleService rules) {
        this.users = users; this.encoder = encoder; this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository; this.rules = rules;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(@RequestAttribute("_csrf") CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody Credentials request) {
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        if (!username.matches("[a-z0-9_.-]{3,80}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username");
        if (users.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        UserAccount user = users.save(new UserAccount(UUID.randomUUID().toString(), username, encoder.encode(request.password())));
        rules.createDefaults(user.id);
        return Map.of("username", username);
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody Credentials request,
                                     HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim().toLowerCase(java.util.Locale.ROOT), request.password()));
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, servletRequest, servletResponse);
        return Map.of("username", authentication.getName());
    }

    @GetMapping("/me")
    public Map<String, String> me(Principal principal) { return Map.of("username", principal.getName()); }
}
