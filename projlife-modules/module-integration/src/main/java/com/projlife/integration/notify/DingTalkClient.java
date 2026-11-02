package com.projlife.integration.notify;

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
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉客户端（SPRINT-INT1，报告①）：
 *  L1 工作通知 asyncsend_v2（旧版 topapi + Query token；task_id 持久化 + 进度回查防"假成功"§六.3）
 *  L2 群机器人 webhook（HmacSHA256 加签 §4.5；20 条/分钟限流）
 *  L3 新版待办 /v1.0/todo/users/{unionId}/tasks（Header 鉴权；任务类触达官方建议用待办）
 *
 * 降级铁律：任何失败不抛异常，统一返回 DingTalkResult(ok=false, reason=...)；审计落 int_sync_log。
 */
@Component
public class DingTalkClient {

    private static final Logger log = LoggerFactory.getLogger(DingTalkClient.class);

    /** 工作通知单次 userid 上限（官方：企业内部应用 5000 人，收敛到 100 分批） */
    static final int MAX_USERIDS_PER_CALL = 100;

    /** 工作通知 msg ≤2048 字节 */
    static final int MAX_MSG_BYTES = 2048;

    public static final String ADAPTER_CODE = "DINGTALK";

    private final DingTalkConfig config;
    private final DingTalkTokenManager tokenManager;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private IntegrationAuditRecorder auditRecorder;

    public DingTalkClient(DingTalkConfig config, DingTalkTokenManager tokenManager,
                          RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.config = config;
        this.tokenManager = tokenManager;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 统一结果载体 */
    public static class DingTalkResult {
        public boolean ok;
        public String channel;      // WORK_NOTIFY / ROBOT / TODO
        public String remoteId;     // task_id / todo id
        public String errcode;
        public String reason;
        public boolean checked;     // 是否已回查进度（防假成功）

        public static DingTalkResult ok(String channel, String remoteId) {
            DingTalkResult r = new DingTalkResult();
            r.ok = true;
            r.channel = channel;
            r.remoteId = remoteId;
            return r;
        }

        public static DingTalkResult fail(String channel, String reason) {
            DingTalkResult r = new DingTalkResult();
            r.ok = false;
            r.channel = channel;
            r.reason = reason;
            return r;
        }
    }

    // ==================== L1 工作通知 ====================

    /**
     * 发送工作通知（markdown）。userIds 超 100 自动分批（每批一次调用，返回最后一批结果+各批 remoteId 以 | 连接）。
     * 发送成功后立即回查一次进度（假成功防线第一跳；延时态由 OPS 端点二次回查）。
     */
    public DingTalkResult sendWorkNotify(List<String> userIds, String title, String markdownText) {
        if (!config.workNotifyConfigured()) {
            return DingTalkResult.fail("WORK_NOTIFY", "钉钉工作通知凭据未配置（appKey/appSecret/agentId）");
        }
        if (userIds == null || userIds.isEmpty()) {
            return DingTalkResult.fail("WORK_NOTIFY", "接收人为空");
        }
        List<String> trimmed = new ArrayList<>();
        for (String id : userIds) {
            if (id != null && !id.trim().isEmpty()) {
                trimmed.add(id.trim());
            }
        }
        if (trimmed.isEmpty()) {
            return DingTalkResult.fail("WORK_NOTIFY", "接收人为空");
        }
        DingTalkResult last = null;
        StringBuilder remoteIds = new StringBuilder();
        for (int from = 0; from < trimmed.size(); from += MAX_USERIDS_PER_CALL) {
            List<String> batch = trimmed.subList(from, Math.min(from + MAX_USERIDS_PER_CALL, trimmed.size()));
            last = sendWorkNotifyBatch(batch, title, markdownText);
            if (last.remoteId != null) {
                if (remoteIds.length() > 0) {
                    remoteIds.append('|');
                }
                remoteIds.append(last.remoteId);
            }
            if (!last.ok) {
                break;
            }
        }
        if (last != null) {
            last.remoteId = remoteIds.length() > 0 ? remoteIds.toString() : null;
        }
        return last == null ? DingTalkResult.fail("WORK_NOTIFY", "无批次执行") : last;
    }

    private DingTalkResult sendWorkNotifyBatch(List<String> userIds, String title, String markdownText) {
        String text = truncateBytes(markdownText == null ? "" : markdownText, MAX_MSG_BYTES - 256);
        try {
            String token = tokenManager.getToken();
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("msgtype", "markdown");
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", title == null ? "通知" : title);
            markdown.put("text", text);
            msg.put("markdown", markdown);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agent_id", Long.valueOf(config.getAgentId()));
            body.put("userid_list", String.join(",", userIds));
            body.put("msg", msg);

            JsonNode resp = postOapi("/topapi/message/corpconversation/asyncsend_v2", token, body);
            String errcode = resp.path("errcode").asText("-1");
            if (!"0".equals(errcode)) {
                DingTalkResult fail = DingTalkResult.fail("WORK_NOTIFY",
                        "errcode=" + errcode + " errmsg=" + resp.path("errmsg").asText(""));
                audit(ADAPTER_CODE, "PUSH", null, "FAILED", fail.reason, toJson(body));
                return fail;
            }
            String taskId = resp.path("task_id").asText("");
            // 官方要求异步 task_id 必须持久化保存（§六.4）
            DingTalkResult ok = DingTalkResult.ok("WORK_NOTIFY", taskId);
            audit(ADAPTER_CODE, "PUSH", taskId, "SUCCESS", null, toJson(body));
            // 假成功第一跳回查（不改变 ok，只标记 checked 与 reason）
            checkProgress(ok, taskId);
            return ok;
        } catch (Exception ex) {
            DingTalkResult fail = DingTalkResult.fail("WORK_NOTIFY", ex.getMessage());
            audit(ADAPTER_CODE, "PUSH", null, "FAILED", fail.reason, null);
            return fail;
        }
    }

    /** 回查发送进度（防"超限假成功"§六.3）：FAILED 显式降级标记 */
    private void checkProgress(DingTalkResult result, String taskId) {
        try {
            String token = tokenManager.getToken();
            JsonNode resp = postOapi("/topapi/message/corpconversation/getsendprogress", token,
                    mapOf("agent_id", Long.valueOf(config.getAgentId()), "task_id", Long.valueOf(taskId)));
            if ("0".equals(resp.path("errcode").asText("-1"))) {
                int progress = resp.path("progress").asInt(0);
                result.checked = true;
                if (progress < 0) {
                    // 官方口径：进度 -1 视为发送失败
                    result.ok = false;
                    result.reason = "工作通知回查进度为失败(progress=" + progress + ")";
                    audit(ADAPTER_CODE, "PUSH", taskId, "FAILED", result.reason, null);
                }
            }
        } catch (Exception ex) {
            log.debug("工作通知进度回查失败（不阻塞）taskId={} err={}", taskId, ex.getMessage());
        }
    }

    /** OPS 二次回查：getsendprogress + getsendresult（假成功终判） */
    public DingTalkResult querySendResult(String taskId) {
        try {
            String token = tokenManager.getToken();
            JsonNode progress = postOapi("/topapi/message/corpconversation/getsendprogress", token,
                    mapOf("agent_id", Long.valueOf(config.getAgentId()), "task_id", Long.valueOf(taskId)));
            JsonNode sendResult = postOapi("/topapi/message/corpconversation/getsendresult", token,
                    mapOf("agent_id", Long.valueOf(config.getAgentId()), "task_id", Long.valueOf(taskId)));
            DingTalkResult r = DingTalkResult.ok("WORK_NOTIFY", taskId);
            r.checked = "0".equals(progress.path("errcode").asText("-1"));
            r.errcode = "progress=" + progress.toString() + " result=" + sendResult.toString();
            return r;
        } catch (Exception ex) {
            return DingTalkResult.fail("WORK_NOTIFY", "回查失败：" + ex.getMessage());
        }
    }

    // ==================== L2 群机器人 ====================

    /**
     * 群机器人 markdown 消息（加签：HmacSHA256(timestamp+"\n"+secret, secret) → Base64 → urlEncode §4.5）。
     * 限流 20 条/分钟，errcode=1301 时显式提示。
     */
    public DingTalkResult sendRobot(String title, String markdownText, boolean atAll) {
        if (!config.robotConfigured()) {
            return DingTalkResult.fail("ROBOT", "钉钉机器人 webhook 未配置");
        }
        try {
            String url = config.getWebhook();
            if (notEmpty(config.getWebhookSecret())) {
                long timestamp = System.currentTimeMillis();
                String sign = signRobot(timestamp, config.getWebhookSecret());
                String sep = url.contains("?") ? "&" : "?";
                url = url + sep + "timestamp=" + timestamp + "&sign=" + URLEncoder.encode(sign, "UTF-8");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", title == null ? "通知" : title);
            markdown.put("text", markdownText == null ? "" : markdownText);
            body.put("markdown", markdown);
            Map<String, Object> at = new HashMap<>();
            at.put("isAtAll", atAll);
            body.put("at", at);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
            String errcode = resp == null ? "-1" : resp.path("errcode").asText("-1");
            if ("0".equals(errcode)) {
                DingTalkResult ok = DingTalkResult.ok("ROBOT", null);
                audit(ADAPTER_CODE, "PUSH", null, "SUCCESS", null, toJson(body));
                return ok;
            }
            String reason = "errcode=" + errcode + " errmsg=" + (resp == null ? "无响应" : resp.path("errmsg").asText(""))
                    + ("1301".equals(errcode) ? "（机器人限流 20 条/分钟）" : "");
            audit(ADAPTER_CODE, "PUSH", null, "FAILED", reason, toJson(body));
            return DingTalkResult.fail("ROBOT", reason);
        } catch (Exception ex) {
            audit(ADAPTER_CODE, "PUSH", null, "FAILED", ex.getMessage(), null);
            return DingTalkResult.fail("ROBOT", ex.getMessage());
        }
    }

    /** 机器人加签（包内可见供测试断言） */
    static String signRobot(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    // ==================== L3 新版待办 ====================

    /**
     * 创建钉钉待办（新版 REST，Header 鉴权）。unionId 与 userid 不同源（A-39：映射随通讯录批次）。
     * dueDate 格式 yyyy-MM-dd HH:mm:ss 或 null。
     */
    public DingTalkResult createTodo(String unionId, String subject, String description, String dueDate) {
        if (!config.tokenConfigured()) {
            return DingTalkResult.fail("TODO", "钉钉待办需 AppKey/AppSecret（unionId 映射随通讯录批次，A-39）");
        }
        if (unionId == null || unionId.isEmpty()) {
            return DingTalkResult.fail("TODO", "unionId 为空");
        }
        try {
            String token = tokenManager.getToken();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("subject", subject == null ? "待办" : subject);
            if (description != null) {
                body.put("description", description);
            }
            if (dueDate != null && !dueDate.isEmpty()) {
                body.put("dueDate", dueDate);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-acs-dingtalk-access-token", token);
            JsonNode resp = restTemplate.postForObject(
                    config.getApiBase() + "/v1.0/todo/users/" + unionId + "/tasks",
                    new HttpEntity<>(body, headers), JsonNode.class);
            if (resp == null || resp.path("id").asText("").isEmpty()) {
                DingTalkResult fail = DingTalkResult.fail("TODO", "待办创建响应异常：" + (resp == null ? "空" : resp.toString()));
                audit(ADAPTER_CODE, "PUSH", null, "FAILED", fail.reason, toJson(body));
                return fail;
            }
            String id = resp.path("id").asText();
            audit(ADAPTER_CODE, "PUSH", id, "SUCCESS", null, toJson(body));
            return DingTalkResult.ok("TODO", id);
        } catch (Exception ex) {
            audit(ADAPTER_CODE, "PUSH", null, "FAILED", ex.getMessage(), null);
            return DingTalkResult.fail("TODO", ex.getMessage());
        }
    }

    // ==================== 通讯录（SPRINT-INT2） ====================

    /** 通用 API 结果（通讯录同步用；每次调用不单独落审计，由同步服务按运行汇总） */
    public static class ApiResult {
        public boolean ok;
        public String reason;
        public JsonNode data;

        public static ApiResult ok(JsonNode data) {
            ApiResult r = new ApiResult();
            r.ok = true;
            r.data = data;
            return r;
        }

        public static ApiResult fail(String reason) {
            ApiResult r = new ApiResult();
            r.ok = false;
            r.reason = reason;
            return r;
        }
    }

    /**
     * 子部门列表（新版 REST，Header 鉴权，报告①§2.1）：GET /v1.0/departments/{deptId}/subDepts
     * → {"result":[{"dept_id":..,"name":..},..]}（A-45：字段容错解析 dept_id/deptId）。
     */
    public ApiResult listSubDepartments(long deptId) {
        if (!config.tokenConfigured()) {
            return ApiResult.fail("钉钉凭据未配置");
        }
        try {
            String token = tokenManager.getToken();
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-acs-dingtalk-access-token", token);
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            JsonNode resp = restTemplate.exchange(
                    config.getApiBase() + "/v1.0/departments/" + deptId + "/subDepts",
                    org.springframework.http.HttpMethod.GET, entity, JsonNode.class).getBody();
            if (resp == null) {
                return ApiResult.fail("子部门接口空响应");
            }
            JsonNode result = resp.has("result") ? resp.path("result") : resp;
            return ApiResult.ok(result);
        } catch (Exception ex) {
            return ApiResult.fail("子部门查询失败：" + ex.getMessage());
        }
    }

    /**
     * 部门用户详情列表（旧版 topapi，Query 鉴权，报告①§2.1）：POST /topapi/v2/user/list
     * body {dept_id, cursor, size≤100} → result.list[{userid,name,unionid,mobile?}], has_more, next_cursor。
     */
    public ApiResult listDeptUsers(long deptId, long cursor) {
        if (!config.tokenConfigured()) {
            return ApiResult.fail("钉钉凭据未配置");
        }
        try {
            String token = tokenManager.getToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("dept_id", deptId);
            body.put("cursor", cursor);
            body.put("size", 100);
            JsonNode resp = restTemplate.postForObject(
                    config.getOapiBase() + "/topapi/v2/user/list?access_token=" + token,
                    new HttpEntity<>(body, headers), JsonNode.class);
            if (resp == null || !"0".equals(resp.path("errcode").asText("-1"))) {
                return ApiResult.fail("部门用户查询失败："
                        + (resp == null ? "空响应" : "errcode=" + resp.path("errcode").asText() + " " + resp.path("errmsg").asText("")));
            }
            return ApiResult.ok(resp.path("result"));
        } catch (Exception ex) {
            return ApiResult.fail("部门用户查询异常：" + ex.getMessage());
        }
    }

    // ==================== 内部 ====================

    /** 旧版 topapi 调用（Query token；认证类 errcode 清缓存重试一次，C1 兜底） */
    private JsonNode postOapi(String path, String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = config.getOapiBase() + path + "?access_token=" + token;
        JsonNode resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
        if (resp == null) {
            throw new IllegalStateException("钉钉接口无响应：" + path);
        }
        String errcode = resp.path("errcode").asText("0");
        if ("88".equals(errcode) || "40001".equals(errcode)) {
            // token 失效：清缓存重取一次
            tokenManager.invalidate();
            url = config.getOapiBase() + path + "?access_token=" + tokenManager.getToken();
            resp = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
            if (resp == null) {
                throw new IllegalStateException("钉钉接口重试无响应：" + path);
            }
        }
        return resp;
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

    /** 按字节截断（中文 3 字节，官方 msg ≤2048 字节口径） */
    static String truncateBytes(String s, int maxBytes) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return s;
        }
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            int len = String.valueOf(c).getBytes(StandardCharsets.UTF_8).length;
            if (count + len > maxBytes) {
                break;
            }
            sb.append(c);
            count += len;
        }
        return sb.toString();
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
