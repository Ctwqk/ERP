package com.example.auth.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues RS256 access tokens: {@code sub} = e-mail, {@code uid} = user id, {@code roles} = {@code ROLE_*} names. */
@Service
public class JwtIssuer {

    private final JwksProvider jwksProvider;
    private final String issuer;
    private final long accessMinutes;

    public JwtIssuer(JwksProvider jwksProvider,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.access-minutes}") long accessMinutes) {
        this.jwksProvider = jwksProvider;
        this.issuer = issuer;
        this.accessMinutes = accessMinutes;
    }

    public String issuer() {
        return issuer;
    }

    public String issueToken(UUID userId, String email, List<String> roles) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plus(Duration.ofMinutes(accessMinutes));

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(email)
                    .claim("uid", userId.toString())
                    .claim("roles", roles) // ["ROLE_ADMIN", ...]
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(jwksProvider.signingKey().getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(jwksProvider.signingKey().toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Could not issue token", e);
        }
    }
}
