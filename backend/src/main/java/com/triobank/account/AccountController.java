package com.triobank.account;

import com.triobank.account.dto.AccountDto;
import com.triobank.common.web.AuthenticatedUser;
import com.triobank.common.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts", description = "The customer's own accounts")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "My accounts",
            description = "Personal accounts first, then by opening time. Blocked accounts are included.")
    @ApiResponse(responseCode = "200", description = "The caller's accounts")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: not a customer",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public List<AccountDto> myAccounts(@Parameter(hidden = true) @CurrentUser AuthenticatedUser caller) {
        return accountService.listForCustomer(caller.customerId());
    }

    @Operation(summary = "Account details", description = "One of the caller's accounts, blocked ones included.")
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: not a customer",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no such account, or not the caller's",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{accountId}")
    public AccountDto account(@Parameter(hidden = true) @CurrentUser AuthenticatedUser caller,
                              @PathVariable UUID accountId) {
        return accountService.getForCustomer(caller.customerId(), accountId);
    }

}
