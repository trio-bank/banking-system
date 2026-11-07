package com.triobank.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of our access tokens, from {@code security.jwt.*}. The app does not start with a weak secret.
 *
 * @param secret HMAC key; its UTF-8 bytes are the key, so at least 32 characters for plain ASCII
 * @param ttl    how long a token stays valid
 */
@ConfigurationProperties("security.jwt")
public record JwtProperties(String secret, @DefaultValue("PT30M") Duration ttl) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            // never put the secret itself into the message
            throw new IllegalArgumentException(
                    "security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("security.jwt.ttl must be positive");
        }
    }

    SecretKey secretKey() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=***, ttl=" + ttl + "]";
    }

}
