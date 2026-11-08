package com.triobank.customer.dto;

import com.triobank.customer.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Residential address. In a request {@code countryCode} may be left out, then it is PL.
 */
@ValidPostalCode
public record AddressDto(
        @Schema(example = "Krakowska")
        @NotBlank @Size(max = 100) String street,

        @Schema(example = "12A")
        @NotBlank @Size(max = 10) String buildingNumber,

        @Schema(example = "4", nullable = true)
        @Size(min = 1, max = 10) String apartmentNumber,

        @Schema(example = "45-018", description = "NN-NNN for Poland")
        @NotBlank @Size(max = 10) String postalCode,

        @Schema(example = "Opole")
        @NotBlank @Size(max = 100) String city,

        @Schema(example = "PL", nullable = true, description = "ISO 3166-1 alpha-2; PL if omitted")
        @Pattern(regexp = "[A-Z]{2}") String countryCode) {

    public static final String DEFAULT_COUNTRY = "PL";

    public static AddressDto from(Address address) {
        return new AddressDto(address.getStreet(), address.getBuildingNumber(), address.getApartmentNumber(),
                address.getPostalCode(), address.getCity(), address.getCountryCode());
    }

    /** Country code, or PL if it was not sent. */
    public String countryOrDefault() {
        return countryCode == null ? DEFAULT_COUNTRY : countryCode;
    }

}
