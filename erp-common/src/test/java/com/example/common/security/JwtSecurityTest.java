package com.example.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtSecurityTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "none").subject("eve@example.com")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void rolesClaimBecomesAuthoritiesWithoutExtraPrefix() {
        JwtAuthenticationConverter converter = new ErpSecurityAutoConfiguration().erpJwtAuthenticationConverter();

        var auth = converter.convert(token().claim("roles", List.of("ROLE_ADMIN", "ROLE_USER")).build());

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void tokenWithoutRolesHasNoAuthorities() {
        var auth = new ErpSecurityAutoConfiguration().erpJwtAuthenticationConverter().convert(token().build());

        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void userIdIsReadFromUidClaim() {
        UUID uid = UUID.randomUUID();
        Jwt jwt = token().claim("uid", uid.toString()).build();

        assertThat(JwtUsers.userId(jwt)).isEqualTo(uid);
        assertThat(JwtUsers.userId(new JwtAuthenticationToken(jwt))).isEqualTo(uid);
        assertThat(JwtUsers.subject(new JwtAuthenticationToken(jwt))).isEqualTo("eve@example.com");
    }

    @Test
    void missingOrMalformedUidIsNull() {
        assertThat(JwtUsers.userId(token().build())).isNull();
        assertThat(JwtUsers.userId(token().claim("uid", "not-a-uuid").build())).isNull();
        assertThat(JwtUsers.userId((org.springframework.security.core.Authentication) null)).isNull();
    }
}
