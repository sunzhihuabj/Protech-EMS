package com.projlife.agent.controller;

import com.projlife.agent.service.AgentService;
import com.projlife.common.api.ApiResponse;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.security.jwt.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 接入接口（V4-06 M19-6 接口约定）：/api/agent/v1/*。
 *
 * <p>该前缀不走 JWT 过滤器（安全白名单），认证方式：
 * /register 用管理端 JWT；其余用 Agent accessToken（client_credentials 2h）+ HMAC 签名。</p>
 */
@RestController
@RequestMapping("/api/agent/v1")
public class AgentController {

    private final AgentService agentService;
    private final JwtUtil jwtUtil;

    public AgentController(AgentService agentService, JwtUtil jwtUtil) {
        this.agentService = agentService;
        this.jwtUtil = jwtUtil;
    }

    /** FR-AGT-01 注册（管理端 JWT，agt:register:manage） */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> identities = (List<String>) body.get("allowedIdentities");
        Integer quotaDaily = body.get("quotaDaily") == null
                ? null : Integer.valueOf(String.valueOf(body.get("quotaDaily")));
        return ApiResponse.ok(agentService.register((String) body.get("platform"),
                identities, quotaDaily));
    }

    /** FR-AGT-02 client_credentials 换取 2h 会话 */
    @PostMapping("/token")
    public ApiResponse<Map<String, Object>> token(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(agentService.token(
                (String) body.get("clientId"),
                (String) body.get("clientSecret"),
                (String) body.get("identityType"),
                body.get("onBehalfOf") == null ? null : Long.valueOf(String.valueOf(body.get("onBehalfOf")))));
    }

    /** FR-AGT-03 能力目录（按身份过滤） */
    @GetMapping("/capabilities")
    public ApiResponse<List<Map<String, Object>>> capabilities(
            @RequestParam(required = false) String identityType) {
        return ApiResponse.ok(agentService.capabilities(identityType));
    }

    /** FR-AGT-05 同步问答（只读 ≤200 行）；签名载荷 timestamp + "." + capability + "." + sessionRef */
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<Map<String, Object>>> message(
            @RequestHeader(value = "X-AGT-Client", required = false) String client,
            @RequestHeader(value = "X-AGT-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-AGT-Signature", required = false) String signature,
            @RequestBody Map<String, Object> body) {
        String sessionRef = sessionRefOf(body);
        String capability = (String) body.get("capability");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        if (client != null) {
            agentService.verifySignature(client, timestamp, signature, signaturePayload(capability, sessionRef));
        }
        return ResponseEntity.status(HttpStatus.OK).body(
                ApiResponse.ok(agentService.message(sessionRef, capability, params)));
    }

    /** FR-AGT-06 异步任务：202 + code=0 + taskId */
    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTask(
            @RequestHeader(value = "X-AGT-Client", required = false) String client,
            @RequestHeader(value = "X-AGT-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-AGT-Signature", required = false) String signature,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {
        String sessionRef = sessionRefOf(body);
        String capability = (String) body.get("capability");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        if (client != null) {
            agentService.verifySignature(client, timestamp, signature, signaturePayload(capability, sessionRef));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.ok(agentService.createTask(sessionRef, capability, params, idempotencyKey)));
    }

    /** 任务状态查询 */
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> taskStatus(
            @RequestHeader(value = "X-AGT-Session", required = false) String sessionRef,
            @PathVariable String taskId) {
        if (sessionRef == null || sessionRef.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "缺少 X-AGT-Session 会话头");
        }
        return ApiResponse.ok(agentService.taskStatus(sessionRef, taskId));
    }

    private String sessionRefOf(Map<String, Object> body) {
        String sessionRef = (String) body.get("sessionRef");
        return sessionRef != null ? sessionRef : AgentService.sessionRefOf((String) body.get("accessToken"));
    }

    private String signaturePayload(String capability, String sessionRef) {
        return capability + "." + sessionRef;
    }
}
