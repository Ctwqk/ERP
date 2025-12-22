package com.example.auth.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwksProvider {

    private final RSAKey rsaJwk;

    public JwksProvider() {
        try {
            this.rsaJwk = new RSAKeyGenerator(2048)
                    .keyID("kid-" + UUID.randomUUID())
                    .generate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public JWKSet jwkSet() {
        return new JWKSet(rsaJwk.toPublicJWK());
    }

    public RSAKey signingKey() {
        return rsaJwk;
    }
}
