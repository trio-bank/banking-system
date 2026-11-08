package com.triobank.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first admin, from {@code security.admin-bootstrap.*} ({@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}).
 * Used only while no admin exists.
 */
@ConfigurationProperties("security.admin-bootstrap")
public record AdminBootstrapProperties(String email, String password) {

    boolean isSet() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminBootstrapProperties[email=" + email + ", password=***]";
    }

}
