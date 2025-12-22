package com.example.auth.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtIssuer {

    private final JwksProvider jwksProvider;

    @Value("${security.jwt.issuer:auth-service}")
    private String issuer;

    @Value("${security.jwt.access-seconds:3600}")
    private long accessSeconds;

    public JwtIssuer(JwksProvider jwksProvider) {
        this.jwksProvider = jwksProvider;
    }

    public String issueToken(UUID userId, String email, List<String> roles) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(accessSeconds);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(email)
                    .claim("uid", userId.toString())
                    .claim("roles", roles) // ["ROLE_ADMIN", ...]
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(jwksProvider.signingKey().getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(jwksProvider.signingKey().toPrivateKey()));

            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
