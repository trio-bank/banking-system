package com.triobank.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing and checking of our HS256 tokens. Spring Security uses the decoder for every request
 * with an {@code Authorization: Bearer} header.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, LockoutProperties.class})
public class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(properties.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // No clock skew: we issue and check tokens on the same server, and 30 minutes means 30 minutes.
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        JwtClaimValidator<Instant> hasExpiry = new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, hasExpiry));
        return decoder;
    }

}
