package com.triobank.common.config;

import com.triobank.auth.JwtService;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless JWT security: no sessions and no cookies, so CSRF protection is not needed.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_POSTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login"
    };

    // Swagger stays reachable through security; outside local/demo springdoc is off, so these paths answer 404.
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problems) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // keeps the original status of a request forwarded to /error
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POSTS).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .build();
    }

    @Bean
    SecurityProblemHandler securityProblemHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new SecurityProblemHandler(resolver);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key"));
        cors.setExposedHeaders(List.of("X-Trace-Id", HttpHeaders.LOCATION));
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /** The {@code role} claim becomes {@code ROLE_CUSTOMER} or {@code ROLE_ADMIN}. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(JwtService.ROLE_CLAIM);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

}
