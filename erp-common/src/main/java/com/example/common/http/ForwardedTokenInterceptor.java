package com.example.common.http;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Propagates the caller's bearer token to downstream ERP services, so a call made on behalf of a user is authorized
 * (and attributed) as that user. Outside a request thread there is no token and nothing is added.
 */
public class ForwardedTokenInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
        }
        return execution.execute(request, body);
    }
}
