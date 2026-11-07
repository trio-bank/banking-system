package com.triobank.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.triobank.auth.StrongPassword;
import com.triobank.common.iban.ValidIban;
import com.triobank.common.pesel.ValidPesel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * The custom annotations produce the right {@code errors[].code}.
 */
class ValidatorFieldErrorCodeTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    record Form(@ValidIban String iban, @ValidPesel String pesel, @StrongPassword String password) {
    }

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void validValuesAndNullsPass() {
        assertThat(VALIDATOR.validate(new Form("PL45 9990 0012 1000 0000 0000 0001", "90051412343", "Str0ng!Passw0rd")))
                .isEmpty();
        assertThat(VALIDATOR.validate(new Form(null, null, null))).isEmpty();
    }

    @Test
    void ibanWithWrongCheckDigitsIsInvalidChecksum() {
        assertThat(codeOf(new Form("PL45999000121000000000000002", null, null)))
                .isEqualTo(FieldErrorCode.INVALID_CHECKSUM);
    }

    @Test
    void ibanWithWrongFormatIsInvalidFormat() {
        assertThat(codeOf(new Form("PL123", null, null))).isEqualTo(FieldErrorCode.INVALID_FORMAT);
    }

    @Test
    void TC03_peselWithWrongCheckDigitIsInvalidChecksum() {
        Form form = new Form(null, "90051412344", null);

        assertThat(codeOf(form)).isEqualTo(FieldErrorCode.INVALID_CHECKSUM);
        assertThat(violationOf(form).getMessage()).isEqualTo("PESEL check digit is invalid.");
    }

    @Test
    void peselWithWrongFormatIsInvalidFormat() {
        assertThat(codeOf(new Form(null, "123", null))).isEqualTo(FieldErrorCode.INVALID_FORMAT);
    }

    @Test
    void peselWithoutRealDateIsInvalidDate() {
        assertThat(codeOf(new Form(null, "90130100004", null))).isEqualTo(FieldErrorCode.INVALID_DATE);
    }

    @Test
    void TC05_passwordWithoutSpecialCharacterIsWeakPassword() {
        assertThat(codeOf(new Form(null, null, "Str0ngPassw0rd"))).isEqualTo(FieldErrorCode.WEAK_PASSWORD);
    }

    @Test
    void tooShortPasswordIsAlsoWeakPassword() {
        assertThat(codeOf(new Form(null, null, "Aa1!"))).isEqualTo(FieldErrorCode.WEAK_PASSWORD);
    }

    private static FieldErrorCode codeOf(Form form) {
        return FieldErrorMapper.codeFor(null, violationOf(form));
    }

    private static ConstraintViolation<Form> violationOf(Form form) {
        Set<ConstraintViolation<Form>> violations = VALIDATOR.validate(form);
        assertThat(violations).hasSize(1);
        return violations.iterator().next();
    }

}
