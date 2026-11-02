package com.projlife.integration.tplus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.common.spi.IntegrationAuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T+ 客户端（SPRINT-INT1，报告③，通道 B 直连）：
 *  Authorization 头 = Base64(JSON{appKey, authInfo(JWT), orgId, access_token})
 *  业务请求 = POST {base}/tplus/api/v2/{Service}/{Method}，Body {"_args":{...}}
 *
 * 13.0 红线（C5 裁决）：不带 type=Asyn（18.0+）；只用基础档案/存货查询等 13.0 已确认接口；
 * 遇「令牌信息不正确 / 提供的服务名称不正确」优先怀疑缺补丁（异常消息内置提示）。
 * EXSM0001（认证缺失）→ 清 token 重试一次。降级铁律：不抛异常，统一 TplusResult。
 */
@Component
public class TplusClient {

    private static final Logger log = LoggerFactory.getLogger(TplusClient.class);

    public static final String ADAPTER_CODE = "TPLUS";

    private final TplusConfig config;
    private final TplusTokenManager tokenManager;
    private final TplusJwtSigner jwtSigner;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private IntegrationAuditRecorder auditRecorder;

    public TplusClient(TplusConfig config, TplusTokenManager tokenManager, TplusJwtSigner jwtSigner,
                       RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.jwtSigner = jwtSigner;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 统一结果载体 */
    public static class TplusResult {
        public boolean ok;
        public String code;
        public String message;
        public JsonNode data;
        public String raw;

        static TplusResult ok(JsonNode data, String raw) {
            TplusResult r = new TplusResult();
            r.ok = true;
            r.data = data;
            r.raw = raw;
            return r;
        }

        static TplusResult fail(String code, String message, String raw) {
            TplusResult r = new TplusResult();
            r.ok = false;
            r.code = code;
            r.message = message;
            r.raw = raw;
            return r;
        }
    }

    /**
     * 通用 v2 调用。service/method 形如 "Inventory"/"Query"（每个接口的 13.0 可用性以官方文档版本标注为准）。
     */
    public TplusResult call(String service, String method, Map<String, Object> args) {
        if (!config.configured()) {
            return TplusResult.fail("NOT_CONFIGURED", "T+ 未配置（appKey/appSecret/orgId/账套凭据）", null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("_args", args == null ? new LinkedHashMap<>() : args);
        try {
            TplusResult r = doCall(service, method, body);
            // 认证失败重试一次（EXSM0001/令牌信息不正确）
            if (!r.ok && isAuthError(r.code, r.message)) {
                tokenManager.invalidate();
                r = doCall(service, method, body);
            }
            audit(ADAPTER_CODE, "PULL", null, r.ok ? "SUCCESS" : "FAILED",
                    r.ok ? null : r.code + " " + r.message, toJson(body));
            return r;
        } catch (Exception ex) {
            audit(ADAPTER_CODE, "PULL", null, "FAILED", ex.getMessage(), toJson(body));
            return TplusResult.fail("EXCEPTION", ex.getMessage(), null);
        }
    }

    /** 存货（库存/存货档案）查询——13.0 实测端点存在（报告③§二） */
    public TplusResult queryInventory(Map<String, Object> args) {
        return call("Inventory", "Query", args);
    }

    /** 通用调用内部实现（构造 Authorization 头） */
    private TplusResult doCall(String service, String method, Map<String, Object> body) {
        try {
            String token = tokenManager.getToken();
            String authInfo;
            try {
                authInfo = jwtSigner.sign(config);
            } catch (IllegalStateException ex) {
                return TplusResult.fail("NO_PRIVATE_KEY", ex.getMessage(), null);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authorizationValue(config.getAppKey(), authInfo, config.getOrgId(), token));

            String url = config.getBaseUrl() + "/tplus/api/v2/" + service + "/" + method;
            JsonNode resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
            if (resp == null) {
                return TplusResult.fail("EMPTY", "T+ 接口空响应：" + service + "/" + method, null);
            }
            String code = resp.path("code").asText("");
            if (!code.isEmpty() && !"0".equals(code) && !"200".equals(code)) {
                String message = resp.path("message").asText("");
                return TplusResult.fail(code, hintPatching(message), resp.toString());
            }
            return TplusResult.ok(resp, resp.toString());
        } catch (HttpStatusCodeException httpEx) {
            String respBody = httpEx.getResponseBodyAsString();
            return TplusResult.fail("HTTP_" + httpEx.getStatusCode().value(),
                    hintPatching(httpEx.getMessage() + " " + respBody), null);
        }
    }

    /** Authorization = Base64(JSON{appKey, authInfo, orgId, access_token})（报告③§三 原文口径） */
    String authorizationValue(String appKey, String authInfo, String orgId, String accessToken) {
        try {
            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put("appKey", appKey);
            auth.put("authInfo", authInfo);
            auth.put("orgId", orgId);
            auth.put("access_token", accessToken);
            String json = objectMapper.writeValueAsString(auth);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Authorization 头构造失败", ex);
        }
    }

    /** 实例存活探测（GET 门户页；有 HTTP 响应即 UP，仿 AI 网关 health 口径） */
    public TplusResult probe() {
        try {
            restTemplate.getForObject(config.getBaseUrl() + "/tplus/view/portal/portal.html", String.class);
            return TplusResult.ok(null, "portal reachable");
        } catch (HttpStatusCodeException httpEx) {
            return TplusResult.ok(null, "portal reachable (http " + httpEx.getStatusCode().value() + ")");
        } catch (ResourceAccessException | IllegalArgumentException ex) {
            return TplusResult.fail("UNREACHABLE", "实例不可达：" + rootMessage(ex), null);
        } catch (Exception ex) {
            return TplusResult.fail("EXCEPTION", ex.getMessage(), null);
        }
    }

    private static boolean isAuthError(String code, String message) {
        String m = (code + " " + (message == null ? "" : message)).toUpperCase();
        return m.contains("EXSM0001") || m.contains("令牌信息不正确") || m.contains("TOKEN");
    }

    private static String hintPatching(String message) {
        if (message != null && (message.contains("令牌信息不正确") || message.contains("服务名称")
                || message.contains("NoAuthOrData") || message.contains("NoAuthOrNotExists"))) {
            return message + "（提示：13.0 需核对补丁版本与接口可用性，见报告③§六）";
        }
        return message;
    }

    private static String rootMessage(Exception ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? ex.getMessage() : t.getMessage();
    }

    private void audit(String adapterCode, String direction, String remoteId, String status,
                       String errorMessage, String payloadJson) {
        if (auditRecorder != null) {
            auditRecorder.record(adapterCode, direction, remoteId, status, errorMessage, payloadJson);
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception ex) {
            return null;
        }
    }
}
