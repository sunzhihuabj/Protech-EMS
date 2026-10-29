package com.projlife.security.kv;

import com.projlife.common.kv.KeyValStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis KV 实现（默认）。memurai/Redis 6379 均兼容。
 */
@Component
@ConditionalOnProperty(name = "projlife.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisKeyValStore implements KeyValStore {

    private final StringRedisTemplate redis;

    public RedisKeyValStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return value == null ? -1 : value;
    }

    @Override
    public long getLong(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Override
    public void put(String key, String value, long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void remove(String key) {
        redis.delete(key);
    }

    @Override
    public boolean exists(String key) {
        Boolean exists = redis.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
