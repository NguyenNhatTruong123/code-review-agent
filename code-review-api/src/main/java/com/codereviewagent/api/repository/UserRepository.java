package com.codereviewagent.api.repository;

import com.codereviewagent.api.model.UserAccount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Provides owner identity lookup and registration uniqueness queries. */
public interface UserRepository extends JpaRepository<UserAccount, String> {
    /**
     * Looks up the normalized username used by session authentication.
     *
     * @param username normalized username
     * @return matching account, or empty when no account exists
     */
    Optional<UserAccount> findByUsername(String username);

    /**
     * Checks registration uniqueness without loading the account.
     *
     * @param username normalized username to check
     * @return {@code true} when an account already uses the username
     */
    boolean existsByUsername(String username);
}
