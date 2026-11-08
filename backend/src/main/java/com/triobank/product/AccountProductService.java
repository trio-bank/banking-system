package com.triobank.product;

import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
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
     * @throws ApiException {@code PRODUCT_NOT_AVAILABLE} if there is no such product or it can't be opened any more
     */
    @Transactional(readOnly = true)
    public AccountProduct getActive(String code) {
        return products.findByCode(code)
                .filter(AccountProduct::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE,
                        "Product " + code + " is not available."));
    }

}
