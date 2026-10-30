package com.projlife.job.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 内部任务调用器（S24 轨道三装配）：以系统账号登录主应用后调用各 job 入口端点。
 * 主应用地址与凭据经环境变量注入；仅 projlife.job.enabled=true 时生效（默认关闭）。
 */
@Component
public class InternalJobInvoker {

    private static final Logger log = LoggerFactory.getLogger(InternalJobInvoker.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${projlife.job.enabled:false}")
    private boolean enabled;

    @Value("${projlife.job.gateway:http://localhost:8080}")
    private String gateway;

    @Value("${projlife.job.system-username:admin@demo}")
    private String username;

    @Value("${projlife.job.system-password:ProjLife@2026}")
    private String password;

    /** 登录换取 JWT 后 POST 指定 job 端点，返回业务数字结果；失败不抛出（任务日志留痕） */
    public int post(String path) {
        if (!enabled) {
            return -1;
        }
        try {
            HttpHeaders loginHeaders = new HttpHeaders();
            loginHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> loginResp = restTemplate.exchange(
                    gateway + "/api/v1/auth/login", HttpMethod.POST,
                    new HttpEntity<>(Map.of("username", username, "password", password, "client", "job"), loginHeaders),
                    Map.class);
            Map<?, ?> loginBody = loginResp.getBody();
            Map<?, ?> loginData = loginBody == null ? null : (Map<?, ?>) loginBody.get("data");
            String token = loginData == null ? null : String.valueOf(loginData.get("accessToken"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null) {
                headers.setBearerAuth(token);
            }
            ResponseEntity<Map> resp = restTemplate.exchange(
                    gateway + path, HttpMethod.POST, new HttpEntity<>(null, headers), Map.class);
            Map<?, ?> body = resp.getBody();
            Object data = body == null ? null : body.get("data");
            int result = data instanceof Number ? ((Number) data).intValue() : 0;
            log.info("内部任务 {} → code={} data={}", path,
                    body == null ? null : body.get("code"), result);
            return result;
        } catch (Exception e) {
            log.warn("内部任务 {} 调用失败：{}", path, e.getMessage());
            return -1;
        }
    }
}
