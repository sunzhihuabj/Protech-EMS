package com.projlife.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.agent.entity.AgtCallLog;
import com.projlife.agent.entity.AgtRegistration;
import com.projlife.agent.entity.AgtSession;
import com.projlife.agent.mapper.AgtCallLogMapper;
import com.projlife.agent.mapper.AgtRegistrationMapper;
import com.projlife.agent.mapper.AgtSessionMapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 接入服务（V4-06 M19）：注册/Token/能力白名单/同步问答/异步任务/blockCode。
 *
 * <p>FR-AGT-02 client_credentials 2h 会话 + HMAC-SHA256 签名；
 * FR-AGT-03 能力目录 12 项按身份过滤；FR-AGT-05 同步问答只读；
 * FR-AGT-06 异步任务 202+code=0；禁止类操作 AGT-FORBIDDEN（422）。</p>
 */
@Service
public class AgentService {

    /** 12 项能力白名单：capability -> 允许身份（FR-AGT-03） */
    private static final Map<String, List<String>> CAPABILITIES = new LinkedHashMap<>();

    /** 只读能力（同步 /messages 可用） */
    private static final List<String> READ_ONLY = Arrays.asList(
            "bid.notice.search", "bid.notice.detail", "crm.customer.search", "mdm.project.search",
            "kb.card.search", "worker.my.task",
            "assistant.project.summary", "assistant.cost.summary", "assistant.schedule.query");

    /** 写能力（异步 /tasks） */
    private static final List<String> WRITE = Arrays.asList(
            "crm.opportunity.create", "worker.report.submit", "worker.attendance.backfill");

    static {
        CAPABILITIES.put("bid.notice.search", Arrays.asList("EXPERT", "ASSISTANT"));
        CAPABILITIES.put("bid.notice.detail", Arrays.asList("EXPERT", "ASSISTANT"));
        CAPABILITIES.put("crm.customer.search", Arrays.asList("EXPERT"));
        CAPABILITIES.put("mdm.project.search", Arrays.asList("EXPERT"));
        CAPABILITIES.put("kb.card.search", Arrays.asList("EXPERT"));
        CAPABILITIES.put("assistant.project.summary", Arrays.asList("ASSISTANT"));
        CAPABILITIES.put("assistant.cost.summary", Arrays.asList("ASSISTANT"));
        CAPABILITIES.put("assistant.schedule.query", Arrays.asList("ASSISTANT"));
        CAPABILITIES.put("worker.my.task", Arrays.asList("WORKER"));
        CAPABILITIES.put("crm.opportunity.create", Arrays.asList("EXPERT"));
        CAPABILITIES.put("worker.report.submit", Arrays.asList("WORKER"));
        CAPABILITIES.put("worker.attendance.backfill", Arrays.asList("WORKER"));
    }

    /** 会话有效期 2h（FR-AGT-02） */
    private static final int SESSION_HOURS = 2;

    private final AgtRegistrationMapper registrationMapper;
    private final AgtSessionMapper sessionMapper;
    private final AgtCallLogMapper callLogMapper;
    private final CodeSequenceService codeSequenceService;
    private final AgentCapabilityExecutor executor;

    public AgentService(AgtRegistrationMapper registrationMapper, AgtSessionMapper sessionMapper,
                        AgtCallLogMapper callLogMapper, CodeSequenceService codeSequenceService,
                        AgentCapabilityExecutor executor) {
        this.registrationMapper = registrationMapper;
        this.sessionMapper = sessionMapper;
        this.callLogMapper = callLogMapper;
        this.codeSequenceService = codeSequenceService;
        this.executor = executor;
    }

    // ---------- 注册（FR-AGT-01） ----------

    /** 管理端注册（JWT + agt:register:manage）；secret 仅返回一次，库中存 SHA-256 摘要 */
    @RequirePermission("agt:register:manage")
    @Transactional
    public Map<String, Object> register(String platform, List<String> allowedIdentities, Integer quotaDaily) {
        if (platform == null || platform.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "platform 必填");
        }
        if (allowedIdentities == null || allowedIdentities.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "allowedIdentities 必填（EXPERT/WORKER/ASSISTANT）");
        }
        for (String identity : allowedIdentities) {
            if (!"EXPERT".equals(identity) && !"WORKER".equals(identity) && !"ASSISTANT".equals(identity)) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "非法身份类型: " + identity);
            }
        }
        String clientId = "agt-" + Integer.toHexString(java.util.UUID.randomUUID().hashCode());
        String secret = java.util.UUID.randomUUID().toString().replace("-", "");

        AgtRegistration reg = new AgtRegistration();
        reg.setClientId(clientId);
        reg.setPlatform(platform);
        // JSON 列要求合法 JSON（List.toString 不是 JSON，MySQL 会拒收）
        try {
            reg.setAllowedIdentities(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(allowedIdentities));
        } catch (Exception e) {
            throw new IllegalStateException("allowedIdentities 序列化失败", e);
        }
        reg.setQuotaDaily(quotaDaily == null ? 10000 : quotaDaily);
        reg.setSecretCipher(sha256(secret));
        reg.setStatus("ACTIVE");
        registrationMapper.insert(reg);

        Map<String, Object> result = new HashMap<>();
        result.put("clientId", clientId);
        result.put("clientSecret", secret);
        result.put("notice", "secret 仅本次返回，请妥善保存");
        return result;
    }

    // ---------- Token（FR-AGT-02 client_credentials 2h） ----------

    @Transactional
    public Map<String, Object> token(String clientId, String secret, String identityType, Long onBehalfOf) {
        AgtRegistration reg = registrationMapper.selectOne(new LambdaQueryWrapper<AgtRegistration>()
                .eq(AgtRegistration::getClientId, clientId).last("LIMIT 1"));
        if (reg == null || !"ACTIVE".equals(reg.getStatus())) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "clientId 无效或已吊销");
        }
        if (!sha256(secret).equals(reg.getSecretCipher())) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "client_secret 校验失败");
        }
        List<String> allowed = splitCsv(reg.getAllowedIdentities());
        if (!allowed.contains(identityType)) {
            throw BizException.blocked("AGT-IDENTITY",
                    "该应用未被授权使用身份 " + identityType + "（FR-AGT-02）", null);
        }
        // WORKER 强制代人（BR-08 联动：工人身份必须落到具体人）
        if ("WORKER".equals(identityType) && onBehalfOf == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "WORKER 身份必须指定 onBehalfOf（代人，BR-08）");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(SESSION_HOURS);
        AgtSession session = new AgtSession();
        session.setSessionRef("sess-" + java.util.UUID.randomUUID().toString().replace("-", ""));
        session.setRegistrationId(reg.getId());
        session.setIdentityType(identityType);
        session.setOnBehalfOf(onBehalfOf);
        session.setStatus("ACTIVE");
        session.setExpiresAt(expiresAt);
        sessionMapper.insert(session);

        String accessToken = signToken(session.getSessionRef(), expiresAt);
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("sessionRef", session.getSessionRef());
        result.put("tokenType", "Bearer");
        result.put("expiresIn", SESSION_HOURS * 3600L);
        result.put("expiresAt", expiresAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("identityType", identityType);
        result.put("capabilities", capabilitiesOf(identityType));
        return result;
    }

    // ---------- 能力目录（FR-AGT-03） ----------

    public List<Map<String, Object>> capabilities(String identityType) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : CAPABILITIES.entrySet()) {
            if (identityType == null || entry.getValue().contains(identityType)) {
                Map<String, Object> cap = new HashMap<>();
                cap.put("capability", entry.getKey());
                cap.put("mode", READ_ONLY.contains(entry.getKey()) ? "SYNC" : "ASYNC");
                cap.put("identities", entry.getValue());
                list.add(cap);
            }
        }
        return list;
    }

    private List<String> capabilitiesOf(String identityType) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : CAPABILITIES.entrySet()) {
            if (entry.getValue().contains(identityType)) {
                list.add(entry.getKey());
            }
        }
        return list;
    }

    // ---------- 同步问答（FR-AGT-05：只读/10s/≤200 行） ----------

    public Map<String, Object> message(String sessionRef, String capability, Map<String, Object> params) {
        AgtSession session = mustActiveSession(sessionRef);
        long start = System.currentTimeMillis();
        try {
            checkCapability(session, capability);
            if (!READ_ONLY.contains(capability)) {
                throw BizException.blocked("AGT-FORBIDDEN",
                        "能力 " + capability + " 为写操作，须走异步任务（FR-AGT-05 同步通道只读）", null);
            }
            Map<String, Object> data = executor.executeSync(capability, session, params);
            Map<String, Object> result = new HashMap<>();
            result.put("capability", capability);
            result.put("data", data);
            return result;
        } catch (BizException e) {
            logCall(session, capability, null, "BLOCKED",
                    String.valueOf(e.getData().get(ErrorCode.BLOCK_CODE)),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        }
    }

    // ---------- 异步任务（FR-AGT-06：202 + code=0 + 状态机） ----------

    @Transactional
    public Map<String, Object> createTask(String sessionRef, String capability,
                                          Map<String, Object> params, String idempotencyKey) {
        AgtSession session = mustActiveSession(sessionRef);
        long start = System.currentTimeMillis();
        checkCapability(session, capability);
        if (!WRITE.contains(capability)) {
            throw BizException.blocked("AGT-FORBIDDEN",
                    "能力 " + capability + " 不在异步白名单或为只读能力", null);
        }
        String taskId = codeSequenceService.next(CodeTypes.agentTask(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"))));
        Map<String, Object> executed = executor.executeAsync(capability, session, params, idempotencyKey, taskId);
        logCall(session, capability, taskId, "ACCEPTED", null,
                (int) (System.currentTimeMillis() - start));
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("status", "ACCEPTED");
        result.put("capability", capability);
        result.putAll(executed);
        return result;
    }

    /** 任务状态机：ACCEPTED → RUNNING → SUCCEEDED/FAILED（演示通道由执行器回填终态） */
    public Map<String, Object> taskStatus(String sessionRef, String taskId) {
        mustActiveSession(sessionRef);
        return executor.taskStatus(taskId);
    }

    // ---------- HMAC 签名校验（FR-AGT-02） ----------

    /** 签名串：timestamp + "." + payload（capability.sessionRef）的 HMAC-SHA256，Base64 输出 */
    public void verifySignature(String clientId, String timestamp, String signature, String payload) {
        if (timestamp == null || signature == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "缺少 X-AGT-Timestamp / X-AGT-Signature");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "X-AGT-Timestamp 非法");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > 5 * 60 * 1000L) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "签名时间窗超限（±5 分钟）");
        }
        AgtRegistration reg = registrationMapper.selectOne(new LambdaQueryWrapper<AgtRegistration>()
                .eq(AgtRegistration::getClientId, clientId).last("LIMIT 1"));
        if (reg == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "clientId 无效");
        }
        String expected = hmacSha256(sha256(reg.getSecretCipher()), timestamp + "." + nvl(payload));
        if (!expected.equals(signature)) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "HMAC 签名校验失败");
        }
    }

    // ---------- 内部 ----------

    private AgtSession mustActiveSession(String sessionRef) {
        AgtSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AgtSession>()
                .eq(AgtSession::getSessionRef, sessionRef).last("LIMIT 1"));
        if (session == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "会话不存在");
        }
        if ("REVOKED".equals(session.getStatus())) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "会话已吊销");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus("CLOSED");
            sessionMapper.updateById(session);
            logCall(session, "session", null, "BLOCKED", "AGT-EXPIRED", 0);
            throw BizException.of(ErrorCode.UNAUTHORIZED, "会话已过期（2h，FR-AGT-02）");
        }
        return session;
    }

    private void checkCapability(AgtSession session, String capability) {
        List<String> allowed = CAPABILITIES.get(capability);
        // 禁止类：不在白名单的能力一律 AGT-FORBIDDEN（含 delete/contract.sign/price.approve 等危险命名）
        if (allowed == null) {
            throw BizException.blocked("AGT-FORBIDDEN",
                    "能力 " + capability + " 不在白名单，禁止调用（FR-AGT-03）", null);
        }
        if (!allowed.contains(session.getIdentityType())) {
            throw BizException.blocked("AGT-FORBIDDEN",
                    "身份 " + session.getIdentityType() + " 无权调用 " + capability, null);
        }
    }

    private void logCall(AgtSession session, String capability, String taskId,
                         String status, String blockCode, int latencyMs) {
        AgtCallLog log = new AgtCallLog();
        log.setRegistrationId(session.getRegistrationId());
        log.setSessionId(session.getId());
        log.setCapability(capability);
        log.setTaskId(taskId);
        log.setResponseStatus(status);
        log.setBlockCode(blockCode);
        log.setLatencyMs(latencyMs);
        callLogMapper.insert(log);
    }

    static List<String> splitCsv(String json) {
        List<String> list = new ArrayList<>();
        for (String s : json.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
            if (!s.trim().isEmpty()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String hmacSha256(String key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** token = Base64(sessionRef) + "." + HMAC(secretDigest, sessionRef + "." + epoch) */
    private String signToken(String sessionRef, LocalDateTime expiresAt) {
        long epoch = expiresAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        String payload = Base64.getEncoder().encodeToString(sessionRef.getBytes(StandardCharsets.UTF_8));
        return payload + "." + hmacSha256(sha256(sessionRef), sessionRef + "." + epoch);
    }

    /** 供控制器解析会话（token 反解 sessionRef） */
    public static String sessionRefOf(String token) {
        try {
            return new String(Base64.getDecoder().decode(token.substring(0, token.indexOf('.'))),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "accessToken 非法");
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
