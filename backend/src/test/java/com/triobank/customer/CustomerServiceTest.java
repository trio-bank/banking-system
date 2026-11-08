package com.triobank.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.triobank.auth.Role;
import com.triobank.auth.User;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.dto.AddressDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    /** Born 1990-05-14. */
    private static final String PESEL = "90051412343";
    /** Born 2008-11-08. */
    private static final String PESEL_2008_11_08 = "08310812342";

    private final CustomerRepository customers = mock(CustomerRepository.class);

    @Test
    void adultWithNewPeselCanRegister() {
        assertThatCode(() -> service("2026-11-08T12:00:00Z").checkCanRegister(PESEL)).doesNotThrowAnyException();
    }

    @Test
    void takenPeselIsRejected() {
        when(customers.existsByPesel(PESEL)).thenReturn(true);

        assertThatThrownBy(() -> service("2026-11-08T12:00:00Z").checkCanRegister(PESEL))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PESEL_ALREADY_REGISTERED));
    }

    @Test
    void eighteenthBirthdayCountsInBankTimeZone() {
        // 23:30 UTC on Nov 7 is already Nov 8 in Warsaw, the 18th birthday.
        assertThatCode(() -> service("2026-11-07T23:30:00Z").checkCanRegister(PESEL_2008_11_08))
                .doesNotThrowAnyException();
    }

    @Test
    void dayBeforeEighteenthBirthdayIsUnderage() {
        assertThatThrownBy(() -> service("2026-11-07T12:00:00Z").checkCanRegister(PESEL_2008_11_08))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CUSTOMER_UNDERAGE));
    }

    @Test
    void createsCustomerWithNumberBirthDateAndDefaultCountry() {
        when(customers.nextCustomerNumber()).thenReturn("10000001");
        when(customers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        User user = new User("anna@example.com", "hash", Role.CUSTOMER);

        Customer customer = service("2026-11-08T12:00:00Z").create(user, new NewCustomer(" Anna ", "Nowak", PESEL,
                "+48501234567", new AddressDto("Krakowska", "12A", " ", "45-018", "Opole", null)));

        assertThat(customer.getUser()).isSameAs(user);
        assertThat(customer.getCustomerNumber()).isEqualTo("10000001");
        assertThat(customer.getFirstName()).isEqualTo("Anna");
        assertThat(customer.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(customer.getResidentialAddress().getCountryCode()).isEqualTo("PL");
        assertThat(customer.getResidentialAddress().getApartmentNumber()).isNull();
    }

    private CustomerService service(String now) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
        return new CustomerService(customers, clock, new BankProperties("99900012", "PLN", WARSAW));
    }

}
