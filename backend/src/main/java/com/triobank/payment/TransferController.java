package com.triobank.payment;

import com.triobank.common.web.AuthenticatedUser;
import com.triobank.common.web.CurrentUser;
import com.triobank.ledger.idempotency.IdempotencyKey;
import com.triobank.ledger.idempotency.IdempotentExecutor;
import com.triobank.ledger.idempotency.IdempotentRequest;
import com.triobank.ledger.idempotency.IdempotentResult;
import com.triobank.ledger.idempotency.RequestHash;
import com.triobank.payment.dto.TransactionDto;
import com.triobank.payment.dto.TransactionMapper;
import com.triobank.payment.dto.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Transfers to own accounts and to other Trio Bank customers")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;
    private final IdempotentExecutor idempotentExecutor;

    public TransferController(TransferService transferService, IdempotentExecutor idempotentExecutor) {
        this.transferService = transferService;
        this.idempotentExecutor = idempotentExecutor;
    }

    @Operation(summary = "Send a transfer",
            description = "From one of your accounts to an account in Trio Bank, found by IBAN. To your own "
                    + "account it is an OWN_TRANSFER and the recipient name is ignored; to another customer it "
                    + "is an INTERNAL_TRANSFER and the recipient name is required. Without a title the default "
                    + "one for the type is used. Sending the same request again with the same Idempotency-Key "
                    + "returns the original transaction with 200.")
    @ApiResponse(responseCode = "201", description = "Sent. Location: /api/v1/transactions/{transactionId}")
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
    @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND: no such source account, or not the caller's",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "CURRENCY_NOT_SUPPORTED, EXTERNAL_TRANSFER_NOT_SUPPORTED, "
            + "RECIPIENT_ACCOUNT_NOT_FOUND, SAME_ACCOUNT_TRANSFER, TRANSFER_POLICY_VIOLATION, "
            + "SOURCE_ACCOUNT_UNAVAILABLE, RECIPIENT_ACCOUNT_UNAVAILABLE, INSUFFICIENT_FUNDS",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<TransactionDto> transfer(
            @Parameter(hidden = true) @CurrentUser AuthenticatedUser caller,
            @Parameter(description = "UUID made by the client; the same one on retries")
            @RequestHeader(name = IdempotencyKey.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        String key = IdempotencyKey.require(idempotencyKey);
        // the request is already cleaned up (IBAN without spaces, trimmed texts), so small differences in
        // typing give the same hash
        String hash = RequestHash.of("TRANSFER", request.sourceAccountId(), request.targetIban(),
                request.recipientName(), request.title(), request.amount(),
                transferService.resolveCurrency(request.currency()));
        IdempotentResult<TransactionDto> result = idempotentExecutor.execute(
                new IdempotentRequest(caller.userId(), key, hash),
                () -> transferService.transfer(caller.userId(), caller.customerId(), request, key, hash),
                stored -> TransactionMapper.toDto(stored, caller.customerId()));
        return IdempotentResponses.of(result);
    }

}
