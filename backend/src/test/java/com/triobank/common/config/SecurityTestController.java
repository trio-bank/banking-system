package com.triobank.common.config;

import com.triobank.common.web.AuthenticatedUser;
import com.triobank.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints behind the real security rules for {@link SecurityIntegrationTest}. */
@RestController
class SecurityTestController {

    @GetMapping("/api/v1/test-security/me")
    AuthenticatedUser me(@CurrentUser AuthenticatedUser user) {
        return user;
    }

    @GetMapping("/api/v1/admin/test-security/ping")
    String adminPing() {
        return "pong";
    }

}
