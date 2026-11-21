package com.triobank.account;

import com.triobank.account.dto.AccountDto;
import com.triobank.account.dto.OpenAccountRequest;
import com.triobank.account.dto.RenameAccountRequest;
import com.triobank.common.web.AuthenticatedUser;
import com.triobank.common.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts", description = "The customer's own accounts")
@RestController
@RequestMapping(AccountController.BASE_PATH)
public class AccountController {

    static final String BASE_PATH = "/api/v1/accounts";

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

    @Operation(summary = "Open an account",
            description = "Opens an account of an active product. Without a name the account gets the product name. "
                    + "A savings account needs an active personal account.")
    @ApiResponse(responseCode = "201", description = "Opened. Location: /api/v1/accounts/{accountId}")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: not a customer",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no product with this code",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = "PRODUCT_NOT_AVAILABLE, ACCOUNT_LIMIT_REACHED, PREREQUISITE_ACCOUNT_REQUIRED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<AccountDto> open(@Parameter(hidden = true) @CurrentUser AuthenticatedUser caller,
                                           @Valid @RequestBody OpenAccountRequest request,
                                           HttpServletRequest httpRequest) {
        AccountDto account = accountService.open(caller.customerId(), request, httpRequest.getRemoteAddr());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + account.id())).body(account);
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

    @Operation(summary = "Rename an account",
            description = "The name is trimmed and must be 1-50 characters. Only an active account can be renamed.")
    @ApiResponse(responseCode = "200", description = "The renamed account")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: not a customer",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no such account, or not the caller's",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION: changed at the same time, retry",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "OPERATION_NOT_ALLOWED_FOR_ACCOUNT: account is not active",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/{accountId}")
    public AccountDto rename(@Parameter(hidden = true) @CurrentUser AuthenticatedUser caller,
                             @PathVariable UUID accountId,
                             @Valid @RequestBody RenameAccountRequest request,
                             HttpServletRequest httpRequest) {
        return accountService.rename(caller.customerId(), accountId, request, httpRequest.getRemoteAddr());
    }

}
