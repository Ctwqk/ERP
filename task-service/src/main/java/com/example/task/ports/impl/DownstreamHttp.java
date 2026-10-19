package com.example.task.ports.impl;

import com.example.common.http.ForwardedTokenInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Builds {@link RestClient}s that call other ERP services on behalf of the current caller (token forwarding). */
@Component
@ConditionalOnProperty(name = "erp.ports.mode", havingValue = "http", matchIfMissing = true)
public class DownstreamHttp {

    private final RestClient.Builder builder;

    public DownstreamHttp(RestClient.Builder builder) {
        this.builder = builder;
    }

    /** Timeouts come from {@code spring.http.client.*}. */
    RestClient clientFor(String baseUrl) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor(new ForwardedTokenInterceptor())
                .build();
    }

    /** Result of a lookup: found, definitely absent, or answered by someone not allowed to tell us. */
    enum Lookup { FOUND, MISSING }

    Lookup lookup(RestClient client, String service, String uri, Object... vars) {
        HttpStatusCode status;
        try {
            status = client.get().uri(uri, vars).exchange((req, res) -> res.getStatusCode());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, service + " is unavailable", ex);
        }
        if (status.is2xxSuccessful()) {
            return Lookup.FOUND;
        }
        if (status.value() == 404) {
            return Lookup.MISSING;
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                service + " answered " + status.value() + " for " + uri);
    }

}
