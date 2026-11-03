package com.projlife.security.web;

import com.projlife.security.config.SecurityProperties;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 内部任务端点共享密钥过滤器（SPRINT-UID-R17，落实硬约束"内部端点必须共享密钥头校验"）。
 *
 * <p>守卫 /api/internal/jobs/**（job 进程调用）与 /api/internal/bid/**（DMZ 拉模式预留）：
 * 请求必须携带 X-Internal-Key 头且与 projlife.security.internal-key 一致，否则 401。
 * 生产经 PROJLIFE_INTERNAL_KEY 注入；常量时间比较防时序侧信道。</p>
 */
public class InternalKeyFilter extends OncePerRequestFilter {

    /** X-Internal-Key 头名（DEPLOYMENT.md 凭据清单第 10 项） */
    public static final String HEADER = "X-Internal-Key";

    private static final String[] GUARDED = {"/api/internal/jobs/", "/api/internal/bid/"};

    private final SecurityProperties props;

    public InternalKeyFilter(SecurityProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isGuarded(path) && !matches(request.getHeader(HEADER))) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":2001,\"message\":\"内部端点密钥缺失或不匹配\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isGuarded(String path) {
        for (String prefix : GUARDED) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        // 精确前缀 /api/internal/jobs 本身（无尾斜杠）
        return path.equals("/api/internal/jobs") || path.equals("/api/internal/bid");
    }

    /** 常量时间比较：SHA-256 摘要后逐字节异或，无短路返回 */
    private boolean matches(String provided) {
        String expected = props.getInternalKey();
        if (provided == null || expected == null || expected.isEmpty()) {
            return false;
        }
        byte[] a = sha256(provided);
        byte[] b = sha256(expected);
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
