package com.projlife.common.kv;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存 KV 实现：无 Redis 时兜底（ADR-005）。仅用于单机验证，多副本部署必须用 Redis 实现。
 */
@Component
@ConditionalOnProperty(name = "projlife.redis.enabled", havingValue = "false")
public class InMemoryKeyValStore implements KeyValStore {

    private final Map<String, Expirable> store = new ConcurrentHashMap<>();

    @Override
    public long increment(String key, long ttlSeconds) {
        long now = System.currentTimeMillis();
        Expirable exp = store.compute(key, (k, old) -> {
            if (old == null || old.expireAt < now) {
                return new Expirable(new AtomicLong(0), now + ttlSeconds * 1000);
            }
            return old;
        });
        return exp.value.incrementAndGet();
    }

    @Override
    public long getLong(String key) {
        Expirable exp = store.get(key);
        if (exp == null || exp.expireAt < System.currentTimeMillis()) {
            return -1;
        }
        return exp.value.get();
    }

    @Override
    public void put(String key, String value, long ttlSeconds) {
        store.put(key, new Expirable(new AtomicLong(0), System.currentTimeMillis() + ttlSeconds * 1000, value));
    }

    @Override
    public String get(String key) {
        Expirable exp = store.get(key);
        if (exp == null || exp.expireAt < System.currentTimeMillis()) {
            return null;
        }
        return exp.str;
    }

    @Override
    public void remove(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return get(key) != null;
    }

    private static class Expirable {
        final AtomicLong value;
        final long expireAt;
        final String str;

        Expirable(AtomicLong value, long expireAt) {
            this(value, expireAt, null);
        }

        Expirable(AtomicLong value, long expireAt, String str) {
            this.value = value;
            this.expireAt = expireAt;
            this.str = str;
        }
    }
}
