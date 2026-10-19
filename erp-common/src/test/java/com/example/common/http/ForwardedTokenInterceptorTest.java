package com.example.common.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ForwardedTokenInterceptorTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private String authorizationSentFor(boolean authenticated) throws IOException {
        if (authenticated) {
            Jwt jwt = Jwt.withTokenValue("abc.def.ghi").header("alg", "none").subject("a@b.c")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(30)).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        }
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://svc/x"));
        ClientHttpRequestExecution execution = (req, body) -> new MockClientHttpResponse(new byte[0], 200);
        new ForwardedTokenInterceptor().intercept(request, new byte[0], execution);
        return request.getHeaders().getFirst("Authorization");
    }

    @Test
    void addsBearerHeaderFromTheCurrentJwt() throws IOException {
        assertThat(authorizationSentFor(true)).isEqualTo("Bearer abc.def.ghi");
    }

    @Test
    void leavesRequestsAloneWithoutAuthentication() throws IOException {
        assertThat(authorizationSentFor(false)).isNull();
    }
}
