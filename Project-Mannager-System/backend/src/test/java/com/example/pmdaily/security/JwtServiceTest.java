package com.example.pmdaily.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-at-least-32-chars-long!!";

    private final JwtService jwtService = new JwtService(SECRET, 900_000);

    @Test
    void generateToken_thenParse_returnsAllClaims() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId, "pm.minh", List.of("PROJECT_MANAGER"), List.of("task:update"));

        String token = jwtService.generateAccessToken(principal);
        Claims claims = jwtService.parseClaims(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
        assertThat(jwtService.extractUsername(claims)).isEqualTo("pm.minh");
        assertThat(jwtService.extractRoles(claims)).containsExactly("PROJECT_MANAGER");
        assertThat(jwtService.extractPermissions(claims)).containsExactly("task:update");
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void token_withWrongSecret_isInvalid() {
        JwtService other = new JwtService("another-secret-that-is-also-long-enough", 900_000);
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "admin", List.of(), List.of());
        String token = jwtService.generateAccessToken(principal);

        assertThat(other.isValid(token)).isFalse();
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void expiredToken_isInvalid() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Instant past = Instant.now().minus(Duration.ofMinutes(1));
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThat(jwtService.isValid(expiredToken)).isFalse();
    }

    @Test
    void tamperedToken_isInvalid() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "admin", List.of(), List.of());
        String token = jwtService.generateAccessToken(principal);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void shortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService("too-short", 900_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void blankSecret_failsFast() {
        assertThatThrownBy(() -> new JwtService("  ", 900_000))
                .isInstanceOf(IllegalStateException.class);
    }
}
