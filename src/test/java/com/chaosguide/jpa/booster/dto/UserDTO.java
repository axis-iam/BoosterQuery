package com.chaosguide.jpa.booster.dto;

import java.time.LocalDateTime;

public class UserDTO {

    private String name;

    private String email;

    private String userName;

    private LocalDateTime createdAt;

    public UserDTO() {
    }

    public UserDTO(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public UserDTO(String name, String email, String userName, LocalDateTime createdAt) {
        this.name = name;
        this.email = email;
        this.userName = userName;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
