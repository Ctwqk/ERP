package com.example.myerp.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RolesClaimConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String claimName;

    public RolesClaimConverter(String claimName) {
        this.claimName = claimName;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object v = jwt.getClaims().get(claimName);
        if (v instanceof List<?> list) {
            return list.stream()
                    .filter(x -> x instanceof String)
                    .map(x -> (String) x)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
        }
        return List.of();
    }
}
