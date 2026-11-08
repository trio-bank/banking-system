package com.triobank.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens. Incoming tokens are checked by the {@code JwtDecoder} from {@link JwtConfig},
 * which Spring Security calls for every request.
 */
@Service
public class JwtService {

    /** {@code CUSTOMER} or {@code ADMIN}. */
    public static final String ROLE_CLAIM = "role";
    /** Customer id; admins don't have it. */
    public static final String CUSTOMER_ID_CLAIM = "cid";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param customerId required for a customer, must be null for an admin
     */
    public IssuedToken issue(UUID userId, Role role, UUID customerId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        if ((role == Role.CUSTOMER) != (customerId != null)) {
            throw new IllegalArgumentException("A customer token needs a customer id, an admin token must not have one");
        }

        // JWT times are whole seconds anyway
        Instant issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString());
        if (customerId != null) {
            claims.claim(CUSTOMER_ID_CLAIM, customerId.toString());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, issuedAt, expiresAt);
    }

}
