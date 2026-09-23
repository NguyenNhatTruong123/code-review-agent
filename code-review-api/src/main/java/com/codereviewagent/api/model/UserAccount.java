package com.codereviewagent.api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class UserAccount {
    @Id public String id;
    @Column(nullable = false, unique = true, length = 120) public String username;
    @Column(nullable = false) public String passwordHash;
    protected UserAccount() {}
    public UserAccount(String id, String username, String passwordHash) {
        this.id = id; this.username = username; this.passwordHash = passwordHash;
    }
}
