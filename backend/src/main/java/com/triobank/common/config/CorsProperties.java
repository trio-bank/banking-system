package com.triobank.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Browser origins allowed to call the API, from {@code cors.allowed-origins} (comma-separated).
 */
@ConfigurationProperties("cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {
}
