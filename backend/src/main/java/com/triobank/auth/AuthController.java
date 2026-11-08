package com.triobank.auth;

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

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
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

}
