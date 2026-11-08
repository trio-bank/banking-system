package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00.750Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(30));
    private final JwtService service = new JwtService(
            config.jwtEncoder(properties), properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void customerTokenHasAllClaims() {
        IssuedToken token = service.issue(USER_ID, Role.CUSTOMER, CUSTOMER_ID);

        Jwt jwt = decoderAt(NOW).decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getClaimAsString("cid")).isEqualTo(CUSTOMER_ID.toString());
        assertThat(jwt.getIssuedAt()).isEqualTo("2026-11-08T12:00:00Z");
        assertThat(jwt.getExpiresAt()).isEqualTo("2026-11-08T12:30:00Z");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void adminTokenHasNoCustomerId() {
        Jwt jwt = decoderAt(NOW).decode(service.issue(USER_ID, Role.ADMIN, null).value());

        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.hasClaim("cid")).isFalse();
    }

    @Test
    void everyTokenGetsItsOwnId() {
        String first = decoderAt(NOW).decode(service.issue(USER_ID, Role.ADMIN, null).value()).getId();
        String second = decoderAt(NOW).decode(service.issue(USER_ID, Role.ADMIN, null).value()).getId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void customerIdMustMatchRole() {
        assertThatThrownBy(() -> service.issue(USER_ID, Role.CUSTOMER, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issue(USER_ID, Role.ADMIN, CUSTOMER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenIsRejectedFromTheSecondItExpires() {
        String token = service.issue(USER_ID, Role.ADMIN, null).value();
        Instant expiry = Instant.parse("2026-11-08T12:30:00Z");

        assertThat(decoderAt(expiry.minusSeconds(1)).decode(token)).isNotNull();
        assertThatThrownBy(() -> decoderAt(expiry.plusSeconds(1)).decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void secretShorterThan32BytesIsRejected() {
        assertThatThrownBy(() -> new JwtProperties("0123456789abcdef0123456789abcde", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes")
                .hasMessageNotContaining("0123456789");
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ttlMustBePositive() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secretIsNotPrinted() {
        assertThat(properties.toString()).doesNotContain(SECRET);
    }

    private JwtDecoder decoderAt(Instant now) {
        return config.jwtDecoder(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

}
