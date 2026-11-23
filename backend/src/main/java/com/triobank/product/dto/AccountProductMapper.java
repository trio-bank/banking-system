package com.triobank.product.dto;

import com.triobank.product.AccountProduct;

public final class AccountProductMapper {

    private AccountProductMapper() {
    }

    public static AccountProductDto toDto(AccountProduct product) {
        return new AccountProductDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getDescription(),
                product.getType(),
                product.getCurrency(),
                product.getInterestRate(),
                product.getOutgoingTransferPolicy(),
                product.isAtmAllowed(),
                product.getMaxAccountsPerCustomer(),
                product.getPrerequisiteType());
    }

}
