package com.example.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;

import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class JwtBlacklistService {
    private static final String BLACKLIST_PREFIX = "jwt:bl:";

    private final StringRedisTemplate redis;

    public JwtBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Duration ttl) {
        redis.opsForValue().set(BLACKLIST_PREFIX + jti, "true", ttl.getSeconds(), TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        Boolean isBlacklisted = redis.hasKey(BLACKLIST_PREFIX + jti);
        return isBlacklisted != null && isBlacklisted;
    }

}
