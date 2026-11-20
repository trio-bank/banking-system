package com.triobank.product;

import com.triobank.product.dto.AccountProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account products", description = "Kinds of accounts a customer can open")
@RestController
@RequestMapping("/api/v1/account-products")
public class AccountProductController {

    private final AccountProductService productService;

    public AccountProductController(AccountProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Product catalog", description = "Only products that can be opened now.")
    @ApiResponse(responseCode = "200", description = "Active products")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED: not a customer",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public List<AccountProductDto> products() {
        return productService.listActive();
    }

}
