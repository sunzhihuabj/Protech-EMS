package com.projlife.security.jwt;

import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 签发与解析（16 册 §7.3）。
 * 载荷只放身份标识（uid/工号/用户名/类型），角色与权限实时加载 + 缓存，避免令牌膨胀与授权滞后。
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecurityProperties props;
    private final SecretKey key;

    public JwtUtil(SecurityProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccess(Long userId, String userCode, String username, String userType) {
        return generate(userId, userCode, username, userType, TYPE_ACCESS, props.getAccessTtlSeconds());
    }

    public String generateRefresh(Long userId, String userCode, String username, String userType) {
        return generate(userId, userCode, username, userType, TYPE_REFRESH, props.getRefreshTtlSeconds());
    }

    private String generate(Long userId, String userCode, String username, String userType, String type, long ttlSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", String.valueOf(userId));
        claims.put("uc", userCode);
        claims.put("un", username);
        claims.put("ut", userType);
        claims.put("typ", type);
        // jti 必须放进 claims map：builder.setId 若在 setClaims 之前调用会被整体覆盖
        claims.put("jti", UUID.randomUUID().toString().replace("-", ""));
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析并校验签名/过期；返回载荷，失败抛 401 */
    public Claims parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException ex) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "会话已过期，请重新登录");
        } catch (Exception ex) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "无效令牌");
        }
    }

    /** 计算 token 剩余有效期（秒），供黑名单 TTL 使用 */
    public long remainingSeconds(Claims claims) {
        Date expiration = claims.getExpiration();
        long remain = (expiration.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(remain, 0);
    }
}
