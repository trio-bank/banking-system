package com.triobank.auth;

import com.triobank.auth.dto.LoginRequest;
import com.triobank.auth.dto.LoginResponse;
import com.triobank.auth.dto.RegisterRequest;
import com.triobank.auth.dto.RegistrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration and login")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final URI ME = URI.create("/api/v1/me");
    private static final String PROBLEM_JSON = "application/problem+json";

    private final RegistrationService registrationService;
    private final LoginService loginService;

    public AuthController(RegistrationService registrationService, LoginService loginService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
    }

    @Operation(summary = "Register a customer",
            description = "Creates the user, the customer and a personal account with zero balance. "
                    + "No token is returned; the client logs in afterwards.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Registered. Location: /api/v1/me")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED, PESEL_ALREADY_REGISTERED",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "CUSTOMER_UNDERAGE",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request,
                                                         HttpServletRequest httpRequest) {
        RegistrationResponse response = registrationService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.created(ME).body(response);
    }

    @Operation(summary = "Log in",
            description = "Returns an access token. Five wrong passwords in a row lock the user for 15 minutes. "
                    + "A wrong password and an unknown email give the same error.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Logged in; user.lastLoginAt is the previous login")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "USER_LOCKED (with lockedUntil) or USER_BLOCKED",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return loginService.login(request, httpRequest.getRemoteAddr());
    }

}
