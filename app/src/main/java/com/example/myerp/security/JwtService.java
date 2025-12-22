package com.example.myerp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    private final String issuer;
    private final int accessMinutes;
    private final Key key;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.access-token-minutes}") int accessMinutes) {
        this.issuer = issuer;
        this.accessMinutes = accessMinutes;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(AuthUser user) {
        var now = OffsetDateTime.now();
        var exp = now.plusMinutes(accessMinutes);

        List<String> roles = user.getAuthorities().isEmpty() ? List.of("ROLE_GUEST")
                : user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getUsername())
                .claim("uid", user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", roles)
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(exp.toInstant()))
                .signWith(key)
                .compact();

    }

    public Jws<Claims> parseAndValidate(String token) {
        return Jwts.parser().verifyWith((SecretKey) key).requireIssuer(issuer).build().parseSignedClaims(token);
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.get("uid", String.class));
    }

    public String getEmail(Claims claims) {
        return claims.get("email", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        List<?> roleList = claims.get("role", List.class);
        if (roleList == null) {
            return List.of();
        }
        return roleList.stream().map(Object::toString).toList();
    }

}
