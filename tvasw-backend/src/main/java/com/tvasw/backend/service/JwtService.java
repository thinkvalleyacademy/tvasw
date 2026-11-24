package com.tvasw.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final Key key;
    private final long expirationMs;

    public JwtService(@Value("${tvasw.app.jwt-secret:}") String secret,
                      @Value("${tvasw.app.jwt-expiration-ms}") long expirationMs) {
        if (secret.isBlank()) {
            log.warn("No JWT secret configured; logins will not survive a restart");
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        this.expirationMs = expirationMs;
    }

    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(userId.toString())
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + expirationMs))
            .signWith(key)
            .compact();
    }

    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
