package com.triobank.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.product.dto.AccountProductDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountProductServiceTest {

    private final AccountProductRepository products = mock(AccountProductRepository.class);
    private final AccountProductService service = new AccountProductService(products);

    @Test
    void activeProductIsReturned() {
        AccountProduct savings = product("SAVINGS_STANDARD", true);
        when(products.findByCode("SAVINGS_STANDARD")).thenReturn(Optional.of(savings));

        assertThat(service.getActive("SAVINGS_STANDARD")).isSameAs(savings);
    }

    @Test
    void unknownProductIsNotFound() {
        when(products.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActive("NOPE"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("Product not found.");
                });
    }

    @Test
    void inactiveProductIsNotAvailable() {
        AccountProduct old = product("OLD", false);
        when(products.findByCode("OLD")).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.getActive("OLD"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PRODUCT_NOT_AVAILABLE));
    }

    @Test
    void listsActiveProductsAsDtos() {
        AccountProduct savings = product("SAVINGS_STANDARD", true);
        when(products.findByActiveTrueOrderByCode()).thenReturn(List.of(savings));

        List<AccountProductDto> found = service.listActive();

        assertThat(found).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(savings.getId());
            assertThat(dto.code()).isEqualTo("SAVINGS_STANDARD");
            assertThat(dto.type()).isEqualTo(AccountType.SAVINGS);
            assertThat(dto.interestRate()).isEqualByComparingTo("4.0000");
            assertThat(dto.outgoingTransferPolicy()).isEqualTo(OutgoingTransferPolicy.OWN_ACCOUNTS_ONLY);
            assertThat(dto.atmAllowed()).isFalse();
            assertThat(dto.maxAccountsPerCustomer()).isEqualTo(2);
            assertThat(dto.prerequisiteType()).isEqualTo(AccountType.PERSONAL);
        });
    }

    private static AccountProduct product(String code, boolean active) {
        AccountProduct product = mock(AccountProduct.class);
        when(product.getId()).thenReturn(UUID.randomUUID());
        when(product.getCode()).thenReturn(code);
        when(product.isActive()).thenReturn(active);
        when(product.getType()).thenReturn(AccountType.SAVINGS);
        when(product.getInterestRate()).thenReturn(new BigDecimal("4.0000"));
        when(product.getOutgoingTransferPolicy()).thenReturn(OutgoingTransferPolicy.OWN_ACCOUNTS_ONLY);
        when(product.getMaxAccountsPerCustomer()).thenReturn(2);
        when(product.getPrerequisiteType()).thenReturn(AccountType.PERSONAL);
        return product;
    }

}
