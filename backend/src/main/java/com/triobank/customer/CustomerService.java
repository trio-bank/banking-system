package com.triobank.customer;

import com.triobank.auth.User;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.common.pesel.PeselParser;
import com.triobank.customer.dto.AddressDto;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    public static final int MIN_AGE = 18;

    private final CustomerRepository customers;
    private final Clock clock;
    private final BankProperties bank;

    public CustomerService(CustomerRepository customers, Clock clock, BankProperties bank) {
        this.customers = customers;
        this.clock = clock;
        this.bank = bank;
    }

    /**
     * Checks that a person with this PESEL may become a customer: nobody has registered with it yet,
     * and they are at least 18 today (bank time zone).
     *
     * @param pesel a PESEL that passed validation
     */
    @Transactional(readOnly = true)
    public void checkCanRegister(String pesel) {
        if (customers.existsByPesel(pesel)) {
            throw new ApiException(ErrorCode.PESEL_ALREADY_REGISTERED,
                    "A customer with this PESEL is already registered.");
        }
        LocalDate today = LocalDate.now(clock.withZone(bank.timeZone()));
        if (PeselParser.birthDate(pesel).plusYears(MIN_AGE).isAfter(today)) {
            throw new ApiException(ErrorCode.CUSTOMER_UNDERAGE,
                    "You must be at least " + MIN_AGE + " years old to open an account.");
        }
    }

    /**
     * Saves a new customer for the user. Call {@link #checkCanRegister} first.
     */
    @Transactional
    public Customer create(User user, NewCustomer data) {
        AddressDto address = data.address();
        Address residentialAddress = new Address(address.street().trim(), address.buildingNumber().trim(),
                blankToNull(address.apartmentNumber()), address.postalCode().trim(), address.city().trim(),
                address.countryOrDefault());
        Customer customer = new Customer(user, customers.nextCustomerNumber(), data.firstName().trim(),
                data.lastName().trim(), data.pesel(), PeselParser.birthDate(data.pesel()), data.phoneNumber(),
                residentialAddress);
        return customers.save(customer);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
