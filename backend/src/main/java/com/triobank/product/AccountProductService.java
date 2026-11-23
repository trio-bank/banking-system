package com.triobank.product;

import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.product.dto.AccountProductDto;
import com.triobank.product.dto.AccountProductMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountProductService {

    /** Every new customer gets an account of this product. */
    public static final String PERSONAL_STANDARD = "PERSONAL_STANDARD";

    private final AccountProductRepository products;

    public AccountProductService(AccountProductRepository products) {
        this.products = products;
    }

    /**
     * Products that can be opened now, ordered by code.
     */
    @Transactional(readOnly = true)
    public List<AccountProductDto> listActive() {
        return products.findByActiveTrueOrderByCode().stream()
                .map(AccountProductMapper::toDto)
                .toList();
    }

    /**
     * @throws ApiException {@code RESOURCE_NOT_FOUND} if there is no such product,
     *                      {@code PRODUCT_NOT_AVAILABLE} if it can't be opened any more
     */
    @Transactional(readOnly = true)
    public AccountProduct getActive(String code) {
        AccountProduct product = products.findByCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found."));
        if (!product.isActive()) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE, "Product " + code + " is not available.");
        }
        return product;
    }

}
