package com.projlife.security.token;

import com.projlife.common.kv.KeyValStore;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

/**
 * Token 黑名单（登出/改密后作废，16 册 §7.3）。存 Redis，TTL=剩余有效期。
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "token:blacklist:";

    private final KeyValStore store;

    public TokenBlacklistService(KeyValStore store) {
        this.store = store;
    }

    public void revoke(Claims claims) {
        String jti = claims.getId();
        if (jti == null || jti.isEmpty()) {
            return;
        }
        long ttl = Math.max(claims.getExpiration().getTime() - System.currentTimeMillis(), 1000) / 1000;
        store.put(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isRevoked(Claims claims) {
        String jti = claims.getId();
        return jti != null && store.exists(KEY_PREFIX + jti);
    }
}
