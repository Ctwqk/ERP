package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class JwtIssuerTest {

    private static final String ISSUER = "http://auth-service:8080";

    private static JwtDecoder decoderFor(JwksProvider keys, String expectedIssuer) throws Exception {
        RSAPublicKey pub = keys.jwkSet().getKeys().get(0).toRSAKey().toRSAPublicKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(pub).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(expectedIssuer));
        return decoder;
    }

    @Test
    void issuedTokenCarriesIdentityClaimsAndVerifiesAgainstPublishedKey() throws Exception {
        JwksProvider keys = new JwksProvider(new AuthKeyStoreProps(null, null, "auth-rsa", null));
        JwtIssuer issuer = new JwtIssuer(keys, ISSUER, 30);
        UUID uid = UUID.randomUUID();

        String token = issuer.issueToken(uid, "eve@example.com", List.of("ROLE_ADMIN", "ROLE_USER"));
        Jwt jwt = decoderFor(keys, ISSUER).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("eve@example.com");
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getClaimAsString("uid")).isEqualTo(uid.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_ADMIN", "ROLE_USER");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getExpiresAt()).isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));
        assertThat(jwt.getHeaders()).containsEntry("kid", keys.signingKey().getKeyID());
    }

    @Test
    void publishedJwksNeverContainsPrivateKeyMaterial() {
        JwksProvider keys = new JwksProvider(new AuthKeyStoreProps("", "", "auth-rsa", ""));

        JWKSet set = keys.jwkSet();

        assertThat(set.getKeys()).hasSize(1);
        assertThat(set.getKeys().get(0).isPrivate()).isFalse();
        assertThat(set.toString()).doesNotContain("\"d\"");
    }

    @Test
    void tokenFromAnotherIssuerOrKeyIsRejected() throws Exception {
        JwksProvider ours = new JwksProvider(new AuthKeyStoreProps(null, null, "auth-rsa", null));
        JwksProvider foreign = new JwksProvider(new AuthKeyStoreProps(null, null, "auth-rsa", null));
        String forged = new JwtIssuer(foreign, ISSUER, 30).issueToken(UUID.randomUUID(), "x@y.z", List.of());
        String wrongIssuer = new JwtIssuer(ours, "http://evil", 30).issueToken(UUID.randomUUID(), "x@y.z", List.of());

        assertThatThrownBy(() -> decoderFor(ours, ISSUER).decode(forged)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> decoderFor(ours, ISSUER).decode(wrongIssuer)).isInstanceOf(Exception.class);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        JwksProvider keys = new JwksProvider(new AuthKeyStoreProps(null, null, "auth-rsa", null));
        String expired = new JwtIssuer(keys, ISSUER, -5).issueToken(UUID.randomUUID(), "x@y.z", List.of());

        assertThatThrownBy(() -> decoderFor(keys, ISSUER).decode(expired)).isInstanceOf(Exception.class);
    }

    @Test
    void signingKeyIsLoadedFromPkcs12KeystoreWhenConfigured(@TempDir Path dir) throws Exception {
        Path store = dir.resolve("auth.p12");
        Process keytool = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "auth-rsa", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=erp-test", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", "test-store-pass", "-keypass", "test-store-pass")
                .redirectErrorStream(true).start();
        assertThat(keytool.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(keytool.exitValue()).isZero();

        AuthKeyStoreProps props = new AuthKeyStoreProps(store.toString(), "test-store-pass", "auth-rsa", "");
        JwksProvider first = new JwksProvider(props);
        JwksProvider second = new JwksProvider(props); // e.g. after a restart

        assertThat(first.signingKey().getKeyID()).isEqualTo("auth-rsa");
        assertThat(second.jwkSet().toString()).isEqualTo(first.jwkSet().toString());
        String token = new JwtIssuer(first, ISSUER, 5).issueToken(UUID.randomUUID(), "x@y.z", List.of());
        assertThat(decoderFor(second, ISSUER).decode(token).getSubject()).isEqualTo("x@y.z");
    }

    @Test
    void missingKeystoreFileFailsFast() {
        assertThatThrownBy(() -> new JwksProvider(new AuthKeyStoreProps("/nonexistent/auth.p12", "x", "a", "x")))
                .isInstanceOf(IllegalStateException.class);
    }
}
