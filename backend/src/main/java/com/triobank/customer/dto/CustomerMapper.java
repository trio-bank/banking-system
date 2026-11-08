package com.triobank.customer.dto;

import com.triobank.customer.Customer;

public final class CustomerMapper {

    private static final int VISIBLE_PESEL_DIGITS = 4;

    private CustomerMapper() {
    }

    public static CustomerProfileDto toProfile(Customer customer) {
        return new CustomerProfileDto(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                maskPesel(customer.getPesel()),
                customer.getDateOfBirth(),
                customer.getUser().getEmail(),
                customer.getPhoneNumber(),
                AddressDto.from(customer.getResidentialAddress()),
                customer.getCreatedAt());
    }

    /** {@code 90051412343} → {@code *******2343}. */
    static String maskPesel(String pesel) {
        int hidden = pesel.length() - VISIBLE_PESEL_DIGITS;
        return "*".repeat(hidden) + pesel.substring(hidden);
    }

}
