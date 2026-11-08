package com.triobank.account.dto;

import com.triobank.account.Account;
import com.triobank.product.AccountProduct;
import com.triobank.product.AccountType;

public final class AccountMapper {

    private AccountMapper() {
    }

    /** For customer accounts only; system accounts have no product. */
    public static AccountDto toDto(Account account) {
        AccountProduct product = account.getProduct();
        return new AccountDto(
                account.getId(),
                account.getIban(),
                account.getName(),
                product.getType(),
                product.getCode(),
                product.getName(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus(),
                product.getInterestRate(),
                product.getType() == AccountType.SAVINGS ? account.getAccruedInterest() : null,
                account.getOpenedAt());
    }

}
