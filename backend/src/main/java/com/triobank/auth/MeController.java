package com.triobank.auth;

import com.triobank.auth.dto.CurrentUserDto;
import com.triobank.common.web.AuthenticatedUser;
import com.triobank.common.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration and login")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUserService currentUserService;

    public MeController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Current user",
            description = "For a customer includes the profile with a masked PESEL; for an admin customer is null.")
    @ApiResponse(responseCode = "200", description = "The caller")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public CurrentUserDto me(@Parameter(hidden = true) @CurrentUser AuthenticatedUser caller) {
        return currentUserService.getCurrentUser(caller.userId());
    }

}
