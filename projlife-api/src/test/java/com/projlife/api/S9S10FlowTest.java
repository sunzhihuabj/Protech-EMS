package com.projlife.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S9+S10 E2E（V4-05 M18 + V4-06 M19 验收）：
 * 数据源 SRC 编号/robots 留痕、四层去重 content_hash、100 分制打分分级、
 * 一键转商机 source_type=BID 写回、订阅推送反馈回训、
 * Agent 注册/Token/能力目录/同步问答/禁止类 AGT-FORBIDDEN/异步任务 202。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S9S10FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long sourceId;
    private static Long noticeId;
    private static String clientId;
    private static String clientSecret;
    private static String expertToken;
    private static String sessionRef;

    private static final class Resp {
        final Map<String, Object> body;

        Resp(Map<String, Object> body) {
            this.body = body;
        }

        int code() {
            Object code = body.get("code");
            if (code == null) {
                throw new AssertionError("非标准响应: " + body);
            }
            return ((Number) code).intValue();
        }

        @SuppressWarnings("unchecked")
        <T> T data() {
            return (T) body.get("data");
        }

        Long longData() {
            Object d = body.get("data");
            return d == null ? null : Long.valueOf(String.valueOf(d));
        }

        Map<String, Object> body() {
            return body;
        }
    }

    private Resp get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new Resp(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody());
    }

    private Resp post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new Resp(rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody());
    }

    /** Agent 通道（无 JWT）：附带 HMAC 签名头 */
    private ResponseEntity<Map> agentPost(String path, Map<String, Object> body,
                                          String capability, String session) {
        String ts = String.valueOf(System.currentTimeMillis());
        String payload = capability + "." + session;
        String sig = hmacSha256(sha256Hex(sha256Hex(clientSecret)), ts + "." + payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-AGT-Client", clientId);
        headers.set("X-AGT-Timestamp", ts);
        headers.set("X-AGT-Signature", sig);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private void loginAs(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "12345678", "client", "web");
        token = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

    private String deadline() {
        return LocalDateTime.now().plusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    @Order(1)
    @DisplayName("FR-BID-01/02：数据源 SRC 编号 + robots 留痕启用/禁抓阻断")
    void sourceLifecycle() {
        loginAs("admin@demo");
        Resp created = post("/api/v1/bid/sources",
                Map.of("category", "A_OWNER", "entryUrl", "https://owner-s910.example.com/tenders"));
        assertEquals(0, created.code(), created.body().toString());
        Map<String, Object> source = created.data();
        sourceId = Long.valueOf(String.valueOf(source.get("id")));
        assertTrue(String.valueOf(source.get("sourceCode")).startsWith("SRC-A-"), "SRC 编号");

        // robots 未过（非 200）不得启用
        Resp blocked = post("/api/v1/bid/sources/" + sourceId + "/review",
                Map.of("status", "ACTIVE", "robotsStatusCode", 403));
        assertEquals(5001, blocked.code(), "robots 阻断应 422");
        assertEquals("BID-ROBOTS", ((Map<?, ?>) blocked.body().get("data")).get("blockCode"));

        Resp reviewed = post("/api/v1/bid/sources/" + sourceId + "/review",
                Map.of("status", "ACTIVE", "robotsStatusCode", 200));
        assertEquals(0, reviewed.code());
        assertEquals("ACTIVE", ((Map<?, ?>) reviewed.data()).get("status"));
    }

    @Test
    @Order(2)
    @DisplayName("FR-BID-06：四层去重 — NEW / HASH_DUPLICATE / URL_DUPLICATE（归一）")
    void ingestDedup() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceId", sourceId);
        payload.put("url", "https://owner-s910.example.com/tenders/2026-001?utm_source=rss");
        payload.put("title", "S910 demo 风电场风电机组采购招标公告");
        payload.put("content", "业主招标采购风电机组，含塔筒，要求具备吊装资质");
        payload.put("projectName", "S910 demo 风电场项目");
        payload.put("lotNo", "LOT-01");
        payload.put("budgetAmount", "120000000");
        payload.put("deadlineAt", deadline());
        payload.put("region", "内蒙古");
        payload.put("ownerId", 1);
        Resp first = post("/api/v1/bid/ingest", payload);
        assertEquals(0, first.code(), first.body().toString());
        assertEquals("NEW", ((Map<?, ?>) first.data()).get("dedup"));
        noticeId = Long.valueOf(String.valueOf(((Map<?, ?>) first.data()).get("noticeId")));
        assertTrue(String.valueOf(((Map<?, ?>) first.data()).get("noticeCode")).startsWith("BID-"), "BID 编号");

        // 同内容重放 → content_hash 命中
        Resp replay = post("/api/v1/bid/ingest", payload);
        assertEquals(0, replay.code());
        assertEquals("HASH_DUPLICATE", ((Map<?, ?>) replay.data()).get("dedup"));

        // URL 仅大小写/尾斜杠差异 → 归一后 URL_DUPLICATE
        Map<String, Object> variant = new HashMap<>(payload);
        variant.put("url", "HTTPS://Owner-S910.example.com/tenders/2026-001/");
        variant.put("title", "S910 demo 风电场风电机组采购招标公告（转发）");
        variant.put("content", "不同正文触发 hash 不同，URL 归一层命中");
        Resp dup = post("/api/v1/bid/ingest", variant);
        assertEquals(0, dup.code());
        assertEquals("URL_DUPLICATE", ((Map<?, ?>) dup.data()).get("dedup"));
    }

    @Test
    @Order(3)
    @DisplayName("FR-BID-09：100 分制打分与分级（HIGH ≥70）")
    void scoreLevel() {
        Resp match = get("/api/v1/bid/notices/" + noticeId + "/match");
        assertEquals(0, match.code(), match.body().toString());
        Map<String, Object> data = match.data();
        assertEquals("HIGH", data.get("level"), "命中关键词+业主+金额+截止期应为 HIGH");
        assertTrue(new java.math.BigDecimal(String.valueOf(data.get("score"))).doubleValue() >= 70,
                "总分应 ≥70");
    }

    @Test
    @Order(4)
    @DisplayName("FR-BID-12：一键转商机 + source_type=BID 写回（R04 商务）")
    void convertOpportunity() {
        loginAs("biz@demo");
        Resp converted = post("/api/v1/bid/notices/" + noticeId + "/convert", null);
        assertEquals(0, converted.code(), converted.body().toString());
        Map<String, Object> data = converted.data();
        Long oppId = Long.valueOf(String.valueOf(data.get("opportunityId")));
        assertEquals("BID", data.get("sourceType"));

        Resp opp = get("/api/v1/crm/opportunities/" + oppId);
        assertEquals(0, opp.code());
        assertEquals("BID", ((Map<?, ?>) opp.data()).get("sourceType"), "商机来源写回 BID");
        assertEquals(String.valueOf(noticeId),
                String.valueOf(((Map<?, ?>) opp.data()).get("sourceId")), "source_id=公告 id");

        Resp notice = get("/api/v1/bid/notices/" + noticeId);
        assertEquals("CONVERTED", ((Map<?, ?>) notice.data()).get("status"));
    }

    @Test
    @Order(5)
    @DisplayName("FR-BID-10/13：订阅推送 + 反馈回训校准")
    void subscribePushFeedback() {
        assertEquals(0, post("/api/v1/bid/subscribe",
                Map.of("userId", 1, "keywords", "[风电]")).code());

        // 独立数据源（同源 ≥60s 限频演示：换源不受限），第二条公告
        Resp source2 = post("/api/v1/bid/sources",
                Map.of("category", "B_OEM", "entryUrl", "https://oem-s910.example.com/notice"));
        assertEquals(0, source2.code(), source2.body().toString());
        Long source2Id = Long.valueOf(String.valueOf(((Map<?, ?>) source2.data()).get("id")));
        assertEquals(0, post("/api/v1/bid/sources/" + source2Id + "/review",
                Map.of("status", "ACTIVE", "robotsStatusCode", 200)).code());

        Map<String, Object> second = new HashMap<>();
        second.put("sourceId", source2Id);
        second.put("url", "https://owner-s910.example.com/tenders/2026-002");
        second.put("title", "S910 demo 二期风电项目风机塔筒招标");
        second.put("content", "二期风电项目招标风机及塔筒");
        second.put("projectName", "S910 demo 风电场二期");
        second.put("region", "河北");
        second.put("ownerId", 1);
        Resp ingested = post("/api/v1/bid/ingest", second);
        assertEquals(0, ingested.code());
        Long secondId = Long.valueOf(String.valueOf(((Map<?, ?>) ingested.data()).get("noticeId")));

        Resp pushed = post("/api/v1/bid/notices/" + secondId + "/push", null);
        assertEquals(0, pushed.code(), pushed.body().toString());
        assertTrue(((Number) ((Map<?, ?>) pushed.data()).get("sent")).intValue() >= 1, "订阅者应收到推送");

        Resp feedback = post("/api/v1/bid/feedback",
                Map.of("noticeId", secondId, "feedbackType", "EFFECTIVE", "note", "中标跟进中"));
        assertEquals(0, feedback.code(), feedback.body().toString());

        Resp match = get("/api/v1/bid/notices/" + secondId + "/match");
        assertEquals(1, ((Number) ((Map<?, ?>) match.data()).get("isCorrect")).intValue(),
                "回训应置 is_correct=1");
    }

    @Test
    @Order(6)
    @DisplayName("FR-AGT-01/02：Agent 注册（JWT）+ client_credentials 2h Token")
    void agentRegisterAndToken() {
        loginAs("admin@demo");
        Resp registered = post("/api/agent/v1/register",
                Map.of("platform", "数字员工-标讯助手", "allowedIdentities", java.util.List.of("EXPERT", "WORKER"),
                        "quotaDaily", 5000));
        assertEquals(0, registered.code(), registered.body().toString());
        Map<String, Object> reg = registered.data();
        clientId = (String) reg.get("clientId");
        clientSecret = (String) reg.get("clientSecret");
        assertNotNull(clientSecret, "secret 仅注册时返回一次");

        // 错误 secret → 401（JDK HttpURLConnection 对 401+流式 body 会重试抛异常，改用 java.net.http）
        Resp badToken = postAgentRaw("/api/agent/v1/token",
                "{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"wrong-secret\",\"identityType\":\"EXPERT\"}");
        assertEquals(2001, badToken.code(), "secret 错误应 401");

        // EXPERT 正常换取
        Resp expert = post("/api/agent/v1/token",
                Map.of("clientId", clientId, "clientSecret", clientSecret, "identityType", "EXPERT"));
        assertEquals(0, expert.code(), expert.body().toString());
        Map<String, Object> data = expert.data();
        expertToken = (String) data.get("accessToken");
        sessionRef = (String) data.get("sessionRef");
        assertEquals(7200L, Long.valueOf(String.valueOf(data.get("expiresIn"))).longValue(), "2h 会话");

        // WORKER 必须代人（BR-08）
        Resp workerNoBehalf = post("/api/agent/v1/token",
                Map.of("clientId", clientId, "clientSecret", clientSecret, "identityType", "WORKER"));
        assertEquals(1001, workerNoBehalf.code(), "WORKER 缺 onBehalfOf 应拒绝");
    }

    @Test
    @Order(7)
    @DisplayName("FR-AGT-03/05：能力目录 + 同步问答 + 禁止类 AGT-FORBIDDEN")
    void agentMessagesAndForbidden() {
        Resp caps = get("/api/agent/v1/capabilities");
        assertEquals(0, caps.code());
        assertEquals(12, ((java.util.List<?>) caps.data()).size(), "能力目录 12 项");

        // 只读问答：标讯检索
        ResponseEntity<Map> search = agentPost("/api/agent/v1/messages",
                Map.of("sessionRef", sessionRef, "capability", "bid.notice.search",
                        "params", Map.of("keyword", "风电")),
                "bid.notice.search", sessionRef);
        assertEquals(200, search.getStatusCodeValue());
        assertEquals(0, ((Number) search.getBody().get("code")).intValue());
        Map<?, ?> msgData = (Map<?, ?>) search.getBody().get("data");
        assertNotNull(msgData, "问答响应 data");
        assertNotNull(((Map<?, ?>) msgData.get("data")).get("rows"), "问答行集");

        // 身份越权：EXPERT 调 WORKER 专属
        Resp forbidden = postWithToken("/api/agent/v1/messages",
                Map.of("sessionRef", sessionRef, "capability", "worker.my.task"));
        assertEquals(5001, forbidden.code(), "身份越权应 422");
        assertEquals("AGT-FORBIDDEN", ((Map<?, ?>) forbidden.body().get("data")).get("blockCode"));

        // 非白名单能力（禁止类命名）
        Resp unknown = postWithToken("/api/agent/v1/messages",
                Map.of("sessionRef", sessionRef, "capability", "contract.sign"));
        assertEquals(5001, unknown.code());
        assertEquals("AGT-FORBIDDEN", ((Map<?, ?>) unknown.body().get("data")).get("blockCode"));
    }

    @Test
    @Order(8)
    @DisplayName("FR-AGT-06：异步任务 202+code=0+状态机")
    void agentAsyncTask() {
        ResponseEntity<Map> created = agentPost("/api/agent/v1/tasks",
                Map.of("sessionRef", sessionRef, "capability", "crm.opportunity.create",
                        "params", Map.of("noticeId", noticeId, "customerId", 1,
                                "oppName", "Agent 异步转商机（S910）", "expectAmount", "98000000")),
                "crm.opportunity.create", sessionRef);
        assertEquals(202, created.getStatusCodeValue(), "异步任务应 202");
        assertEquals(0, ((Number) created.getBody().get("code")).intValue(), "202 + code=0");
        String taskId = (String) ((Map<?, ?>) created.getBody().get("data")).get("taskId");
        assertTrue(taskId.startsWith("T-AGT-"), "T-AGT 任务编号");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-AGT-Session", sessionRef);
        ResponseEntity<Map> status = rest.exchange("/api/agent/v1/tasks/" + taskId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertEquals(0, ((Number) status.getBody().get("code")).intValue());
        assertEquals("SUCCEEDED", ((Map<?, ?>) status.getBody().get("data")).get("status"));
    }

    private Resp postWithToken(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        body = new HashMap<>(body);
        body.put("accessToken", expertToken);
        return new Resp(rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody());
    }

    /** Agent 通道裸 POST（java.net.http，不做认证重试），返回标准响应包 */
    private Resp postAgentRaw(String path, String json) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create(rest.getRootUri() + path))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            java.net.http.HttpResponse<String> response =
                    client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            return new Resp(body);
        } catch (Exception e) {
            throw new IllegalStateException("postAgentRaw failed: " + path, e);
        }
    }

    private static String sha256Hex(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hmacSha256(String key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
