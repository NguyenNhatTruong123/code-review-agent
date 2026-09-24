package com.codereviewagent.api.model;

import jakarta.persistence.*;

/** Persisted identity record; the password field stores only an encoded hash. */
@Entity
@Table(name = "app_users")
public class UserAccount {
    @Id public String id;
    @Column(nullable = false, unique = true, length = 120) public String username;
    @Column(nullable = false) public String passwordHash;
    protected UserAccount() {}
    /** Creates an account from an already normalized username and encoded password hash.
     * @param id application account identifier
     * @param username normalized unique username
     * @param passwordHash encoded password hash
     */
    public UserAccount(String id, String username, String passwordHash) {
        this.id = id; this.username = username; this.passwordHash = passwordHash;
    }
}
