package com.projlife.security.web;

import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.security.auth.UserDetailsProvider;
import com.projlife.security.jwt.JwtUtil;
import com.projlife.security.token.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器：解析 Bearer 令牌 → 黑名单校验 → 实时加载登录用户（角色/权限） → 写入安全上下文。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklist;
    private final UserDetailsProvider userDetailsProvider;

    public JwtAuthFilter(JwtUtil jwtUtil, TokenBlacklistService blacklist, UserDetailsProvider userDetailsProvider) {
        this.jwtUtil = jwtUtil;
        this.blacklist = blacklist;
        this.userDetailsProvider = userDetailsProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            if (blacklist.isRevoked(claims)) {
                throw BizException.of(401, "令牌已失效，请重新登录");
            }
            String uid = (String) claims.get("uid");
            LoginUser user = userDetailsProvider.loadById(Long.valueOf(uid));
            if (user == null) {
                throw BizException.of(401, "账号不存在或已停用");
            }
            UserContextHolder.set(user);
            MDC.put("userId", String.valueOf(user.getUserId()));
            MDC.put("username", user.getRealName() != null ? user.getRealName() : user.getUsername());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            if (MDC.get("traceId") == null) {
                MDC.put("traceId", claims.getId());
            }
            chain.doFilter(request, response);
        } catch (BizException ex) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":2001,\"message\":\"" + ex.getMessage() + "\",\"data\":null}");
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
