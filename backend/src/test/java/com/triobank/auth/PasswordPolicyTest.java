package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @Test
    void acceptsStrongTestPassword() {
        assertThat(PasswordPolicy.isStrong("Str0ng!Passw0rd")).isTrue();
    }

    @Test
    void TC05_rejectsPasswordWithoutSpecialCharacter() {
        assertThat(PasswordPolicy.isStrong("Str0ngPassw0rd")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "Sh0rt!Pass1",      // 11 characters
            "str0ng!passw0rd",  // no uppercase
            "STR0NG!PASSW0RD",  // no lowercase
            "Strong!Password",  // no digit
            "Str0ng Passw0rd"   // a space is not a special character
    })
    void rejectsWeakPasswords(String password) {
        assertThat(PasswordPolicy.isStrong(password)).isFalse();
    }

    @Test
    void checksLengthLimits() {
        String base = "Aa1!";
        assertThat(PasswordPolicy.isStrong(base + "x".repeat(8))).isTrue();     // 12
        assertThat(PasswordPolicy.isStrong(base + "x".repeat(124))).isTrue();   // 128
        assertThat(PasswordPolicy.isStrong(base + "x".repeat(125))).isFalse();  // 129
    }

    @Test
    void acceptsPolishLetters() {
        assertThat(PasswordPolicy.isStrong("Źdźbło!trawy12")).isTrue();
    }

    @Test
    void rejectsPasswordContainingEmailName() {
        assertThat(PasswordPolicy.isStrong("Anna.Nowak!2026x", "anna.nowak@example.com")).isFalse();
        assertThat(PasswordPolicy.isStrong("Str0ng!Passw0rd", "anna.nowak@example.com")).isTrue();
    }

    @Test
    void ignoresVeryShortEmailNames() {
        assertThat(PasswordPolicy.isStrong("Str0ng!Passw0rd", "st@example.com")).isTrue();
    }

}
