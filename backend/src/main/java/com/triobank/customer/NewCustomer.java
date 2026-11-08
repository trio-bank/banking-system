package com.triobank.customer;

import com.triobank.customer.dto.AddressDto;

/**
 * Personal data of a customer who is being registered. Already validated by the caller.
 */
public record NewCustomer(
        String firstName,
        String lastName,
        String pesel,
        String phoneNumber,
        AddressDto address) {
}
