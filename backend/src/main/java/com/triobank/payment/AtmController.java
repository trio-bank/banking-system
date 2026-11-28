package com.triobank.payment;

import com.triobank.common.web.AuthenticatedUser;
import com.triobank.common.web.CurrentUser;
import com.triobank.ledger.idempotency.IdempotencyKey;
import com.triobank.ledger.idempotency.IdempotentExecutor;
import com.triobank.ledger.idempotency.IdempotentRequest;
import com.triobank.ledger.idempotency.IdempotentResult;
import com.triobank.ledger.idempotency.RequestHash;
import com.triobank.payment.dto.CashOperationRequest;
import com.triobank.payment.dto.TransactionDto;
import com.triobank.payment.dto.TransactionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ATM", description = "Cash deposits and withdrawals at the simulated ATM")
@RestController
@RequestMapping("/api/v1/atm")
public class AtmController {

    static final String TRANSACTIONS_PATH = "/api/v1/transactions/";

    private final AtmService atmService;
    private final IdempotentExecutor idempotentExecutor;

    public AtmController(AtmService atmService, IdempotentExecutor idempotentExecutor) {
        this.atmService = atmService;
        this.idempotentExecutor = idempotentExecutor;
    }

    @Operation(summary = "Deposit cash",
            description = "Multiples of 10 from 10.00 to 20 000.00 PLN, personal accounts only. "
                    + "Sending the same request again with the same Idempotency-Key returns the original "
                    + "transaction with 200.")
    @ApiResponse(responseCode = "201", description = "Deposited. Location: /api/v1/transactions/{transactionId}")
    @ApiResponse(responseCode = "200", description = "Already done with this Idempotency-Key; the original result")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, IDEMPOTENCY_KEY_MISSING",
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
    @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "CURRENCY_NOT_SUPPORTED, CASH_AMOUNT_INVALID, "
            + "OPERATION_NOT_ALLOWED_FOR_ACCOUNT, RECIPIENT_ACCOUNT_UNAVAILABLE",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/deposits")
    public ResponseEntity<TransactionDto> deposit(
            @Parameter(hidden = true) @CurrentUser AuthenticatedUser caller,
            @Parameter(description = "UUID made by the client; the same one on retries")
            @RequestHeader(name = IdempotencyKey.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CashOperationRequest request) {
        return execute("ATM_DEPOSIT", caller, idempotencyKey, request, atmService::deposit);
    }

    @Operation(summary = "Withdraw cash",
            description = "Multiples of 10 from 10.00 to 5 000.00 PLN, personal accounts only, not more than "
                    + "the balance. Sending the same request again with the same Idempotency-Key returns "
                    + "the original transaction with 200.")
    @ApiResponse(responseCode = "201", description = "Withdrawn. Location: /api/v1/transactions/{transactionId}")
    @ApiResponse(responseCode = "200", description = "Already done with this Idempotency-Key; the original result")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, IDEMPOTENCY_KEY_MISSING",
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
    @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "CURRENCY_NOT_SUPPORTED, CASH_AMOUNT_INVALID, "
            + "OPERATION_NOT_ALLOWED_FOR_ACCOUNT, SOURCE_ACCOUNT_UNAVAILABLE, INSUFFICIENT_FUNDS",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/withdrawals")
    public ResponseEntity<TransactionDto> withdraw(
            @Parameter(hidden = true) @CurrentUser AuthenticatedUser caller,
            @Parameter(description = "UUID made by the client; the same one on retries")
            @RequestHeader(name = IdempotencyKey.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CashOperationRequest request) {
        return execute("ATM_WITHDRAWAL", caller, idempotencyKey, request, atmService::withdraw);
    }

    /** Deposit or withdrawal; both service methods take the same arguments. */
    @FunctionalInterface
    private interface CashOperation {
        TransactionDto run(UUID userId, UUID customerId, CashOperationRequest request, String key, String hash);
    }

    /**
     * Runs the operation once per idempotency key. The operation name is part of the hash,
     * so a key used for a deposit can't be reused for a withdrawal.
     */
    private ResponseEntity<TransactionDto> execute(String operationName, AuthenticatedUser caller,
                                                   String idempotencyKey, CashOperationRequest request,
                                                   CashOperation operation) {
        String key = IdempotencyKey.require(idempotencyKey);
        String hash = RequestHash.of(operationName, request.accountId(), request.amount(),
                atmService.resolveCurrency(request.currency()));
        IdempotentResult<TransactionDto> result = idempotentExecutor.execute(
                new IdempotentRequest(caller.userId(), key, hash),
                () -> operation.run(caller.userId(), caller.customerId(), request, key, hash),
                stored -> TransactionMapper.toDto(stored, caller.customerId()));
        return respond(result);
    }

    private static ResponseEntity<TransactionDto> respond(IdempotentResult<TransactionDto> result) {
        TransactionDto transaction = result.value();
        if (result.replayed()) {
            return ResponseEntity.ok(transaction);
        }
        return ResponseEntity.created(URI.create(TRANSACTIONS_PATH + transaction.id())).body(transaction);
    }

}
