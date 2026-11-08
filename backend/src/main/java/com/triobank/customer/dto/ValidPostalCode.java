package com.triobank.customer.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Polish addresses (also when the country is left out) need a postal code like {@code 45-018}.
 * Other countries have their own formats, so there only the length is checked.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPostalCodeValidator.class)
public @interface ValidPostalCode {

    String message() default "Postal code must look like 00-000.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
