package com.example.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the RSA key pair used to sign access tokens and publishes the public half as a JWKS.
 *
 * <p>With {@code auth.keystore.location} set, the key is loaded from that PKCS12 keystore (stable across restarts and
 * replicas). Otherwise a 2048-bit key is generated in memory, which is fine for local development.
 */
@Component
public class JwksProvider {

    private static final Logger log = LoggerFactory.getLogger(JwksProvider.class);

    private final RSAKey rsaJwk;

    public JwksProvider(AuthKeyStoreProps props) {
        this.rsaJwk = hasText(props.location()) ? load(props) : generate();
    }

    private static RSAKey generate() {
        log.warn("No auth.keystore.location configured: generated an ephemeral RSA signing key. "
                + "Issued tokens stop working when this service restarts.");
        try {
            return new RSAKeyGenerator(2048).keyID("kid-" + UUID.randomUUID()).generate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate RSA signing key", e);
        }
    }

    private static RSAKey load(AuthKeyStoreProps props) {
        Path path = Path.of(props.location());
        if (!Files.isReadable(path)) {
            throw new IllegalStateException("Keystore not readable: " + path);
        }
        try (InputStream in = Files.newInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] storePassword = nullToEmpty(props.password()).toCharArray();
            keyStore.load(in, storePassword);
            char[] keyPassword = hasText(props.keyPassword()) ? props.keyPassword().toCharArray() : storePassword;
            RSAKey key = RSAKey.load(keyStore, props.alias(), keyPassword);
            if (key == null) {
                throw new IllegalStateException("No RSA key with alias '" + props.alias() + "' in " + path);
            }
            log.info("Loaded signing key '{}' from {}", props.alias(), path);
            return key;
        } catch (IOException | java.security.GeneralSecurityException | com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Could not load signing key from " + path, e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public JWKSet jwkSet() {
        return new JWKSet(rsaJwk.toPublicJWK());
    }

    public RSAKey signingKey() {
        return rsaJwk;
    }
}
