package com.projlife.integration.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉钉客户端协议测试（SPRINT-INT1）：JDK HttpServer 桩仿真官方端点，
 * 逐项断言请求结构与签名（token 缓存纪律/agent_id/markdown 结构/HMAC 加签/Header 鉴权/分批/降级链）。
 */
class DingTalkClientTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static HttpServer server;
    private static int port;
    private static final AtomicInteger TOKEN_HITS = new AtomicInteger();
    private static final AtomicInteger ASYNC_HITS = new AtomicInteger();
    private static final AtomicInteger ROBOT_HITS = new AtomicInteger();
    private static final Map<String, String> CAPTURED = new ConcurrentHashMap<>();

    private static final String SECRET = "SEC-test-secret";

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/v1.0/oauth2/accessToken", ex -> {
            TOKEN_HITS.incrementAndGet();
            respond(ex, 200, "{\"accessToken\":\"test-token-1\",\"expireIn\":7200}");
        });
        server.createContext("/topapi/message/corpconversation/asyncsend_v2", ex -> {
            ASYNC_HITS.incrementAndGet();
            CAPTURED.put("async_query", ex.getRequestURI().getQuery());
            CAPTURED.put("async_body", readBody(ex));
            respond(ex, 200, "{\"errcode\":0,\"errmsg\":\"ok\",\"task_id\":9001}");
        });
        server.createContext("/topapi/message/corpconversation/getsendprogress", ex -> {
            String body = readBody(ex);
            CAPTURED.put("progress_body", body);
            // task_id=9002 模拟假成功失败态（progress=-1）
            respond(ex, 200, body.contains("9002")
                    ? "{\"errcode\":0,\"errmsg\":\"ok\",\"progress\":-1}"
                    : "{\"errcode\":0,\"errmsg\":\"ok\",\"progress\":50}");
        });
        server.createContext("/robot/send", ex -> {
            ROBOT_HITS.incrementAndGet();
            CAPTURED.put("robot_query", ex.getRequestURI().getQuery());
            String body = readBody(ex);
            CAPTURED.put("robot_body", body);
            respond(ex, 200, body.contains("RATELIMIT")
                    ? "{\"errcode\":1301,\"errmsg\":\"关键词命中限流\"}"
                    : "{\"errcode\":0,\"errmsg\":\"ok\"}");
        });
        server.createContext("/v1.0/todo/users/", ex -> {
            CAPTURED.put("todo_token_header", ex.getRequestHeaders().getFirst("x-acs-dingtalk-access-token"));
            CAPTURED.put("todo_body", readBody(ex));
            respond(ex, 200, "{\"id\":\"todo-9\"}");
        });
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    // ---- 构造器 ----

    private DingTalkConfig workConfig() {
        DingTalkConfig c = new DingTalkConfig();
        c.setEnabled(true);
        c.setAppKey("ding-app-key");
        c.setAppSecret("ding-app-secret");
        c.setAgentId("110001");
        c.setApiBase("http://127.0.0.1:" + port);
        c.setOapiBase("http://127.0.0.1:" + port);
        return c;
    }

    private DingTalkClient newClient(DingTalkConfig config) {
        return new DingTalkClient(config, new DingTalkTokenManager(config, new RestTemplate(), OM),
                new RestTemplate(), OM);
    }

    // ---- 用例 ----

    @Test
    @DisplayName("工作通知：请求结构（agent_id/userid_list/markdown）+ task_id 持久化 + token 缓存命中一次")
    void workNotify() {
        TOKEN_HITS.set(0);
        ASYNC_HITS.set(0);
        DingTalkConfig config = workConfig();
        DingTalkClient client = newClient(config);

        DingTalkClient.DingTalkResult r = client.sendWorkNotify(java.util.Collections.singletonList("u001"),
                "工单提醒", "#### 新工单\n\n编号 T-1001");

        assertTrue(r.ok, "应成功");
        assertEquals("9001", r.remoteId, "task_id 应持久化返回");
        assertTrue(r.checked, "应已回查进度");
        assertEquals(1, TOKEN_HITS.get(), "两次远端调用（发送+回查）只取一次 token");
        assertEquals(1, ASYNC_HITS.get());

        Map<String, Object> body = readJson(CAPTURED.get("async_body"));
        assertEquals(110001, ((Number) body.get("agent_id")).intValue(), "agent_id 必填");
        assertEquals("u001", body.get("userid_list"));
        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) body.get("msg");
        assertEquals("markdown", msg.get("msgtype"));
        @SuppressWarnings("unchecked")
        Map<String, Object> md = (Map<String, Object>) msg.get("markdown");
        assertEquals("工单提醒", md.get("title"));
        assertTrue(CAPTURED.get("async_query").contains("access_token=test-token-1"), "旧版 topapi 走 Query token");
    }

    @Test
    @DisplayName("工作通知：>100 人自动分批（150 人 → 2 批 2 次调用）")
    void workNotifyBatchSplit() {
        ASYNC_HITS.set(0);
        DingTalkClient client = newClient(workConfig());
        List<String> users = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            users.add("u" + i);
        }
        DingTalkClient.DingTalkResult r = client.sendWorkNotify(users, "批量", "内容");
        assertTrue(r.ok);
        assertEquals(2, ASYNC_HITS.get(), "150 人应分 2 批");
        assertEquals("9001|9001", r.remoteId, "各批 task_id 以 | 连接");
    }

    @Test
    @DisplayName("工作通知：网络不可达 → 失败结果不抛异常（降级铁律）")
    void workNotifyErrcode() {
        DingTalkConfig bad = workConfig();
        bad.setOapiBase("http://127.0.0.1:1");
        DingTalkClient deadClient = new DingTalkClient(bad,
                new DingTalkTokenManager(bad, new RestTemplate(), OM), new RestTemplate(), OM);
        DingTalkClient.DingTalkResult r = deadClient.sendWorkNotify(
                java.util.Collections.singletonList("u1"), "t", "c");
        assertFalse(r.ok, "网络不可达应返回失败结果而非抛异常");
        assertNotNull(r.reason);
    }

    @Test
    @DisplayName("群机器人：HmacSHA256 加签可验算 + errcode 1301 限流提示")
    void robotSignAndRateLimit() throws Exception {
        DingTalkConfig config = workConfig();
        config.setWebhook("http://127.0.0.1:" + port + "/robot/send");
        config.setWebhookSecret(SECRET);
        DingTalkClient client = newClient(config);

        DingTalkClient.DingTalkResult r = client.sendRobot("投标提醒", "新标讯", false);
        assertTrue(r.ok, "加签正确应成功");

        // 服务端重算签名验证（query 中 sign 为 urlEncode 形态，先解码）
        String query = CAPTURED.get("robot_query");
        String timestamp = query.replaceAll(".*timestamp=(\\d+).*", "$1");
        String signEncoded = query.replaceAll(".*sign=([^&]+).*", "$1");
        String sign = java.net.URLDecoder.decode(signEncoded, "UTF-8");
        String stringToSign = timestamp + "\n" + SECRET;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(
                mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, sign, "sign 应为 HmacSHA256(timestamp+\\n+secret)");

        DingTalkClient.DingTalkResult limited = client.sendRobot("限流", "RATELIMIT", false);
        assertFalse(limited.ok);
        assertTrue(limited.reason.contains("限流"), "1301 应带限流提示");
    }

    @Test
    @DisplayName("新版待办：Header 鉴权 + id 持久化")
    void createTodo() {
        DingTalkClient client = newClient(workConfig());
        DingTalkClient.DingTalkResult r = client.createTodo("u123", "采购评审", "请处理", "2026-11-05 10:00:00");
        assertTrue(r.ok);
        assertEquals("todo-9", r.remoteId);
        assertEquals("test-token-1", CAPTURED.get("todo_token_header"), "新版 REST 走 Header 鉴权");
        Map<String, Object> body = readJson(CAPTURED.get("todo_body"));
        assertEquals("采购评审", body.get("subject"));
    }

    @Test
    @DisplayName("msg ≤2048 字节截断")
    void truncateBytes() {
        String longText = "工".repeat(2000);
        String cut = DingTalkClient.truncateBytes(longText, 2048);
        assertTrue(cut.getBytes(StandardCharsets.UTF_8).length <= 2048);
        assertEquals(682, cut.length(), "682×3=2046 ≤2048");
    }

    @Test
    @DisplayName("适配器降级链：L1 断连 → L2 机器人兜底成功；未启用 → false 不抛异常")
    void adapterFallback() {
        // L1 断连（apiBase 指死端口），L2 机器人指向桩
        DingTalkConfig config = workConfig();
        config.setApiBase("http://127.0.0.1:1");
        config.setOapiBase("http://127.0.0.1:1");
        config.setWebhook("http://127.0.0.1:" + port + "/robot/send");
        config.setWebhookSecret(SECRET);
        DingTalkClient client = newClient(config);
        DingTalkNotifyAdapter adapter = new DingTalkNotifyAdapter(config, client);

        assertTrue(adapter.available());
        assertTrue(adapter.send(30L, "审批待办", "合同 C-001 待审批", "approval", "1"),
                "工作通知失败后机器人应兜底成功");

        // 未启用
        DingTalkConfig off = new DingTalkConfig();
        DingTalkNotifyAdapter offAdapter = new DingTalkNotifyAdapter(off,
                newClient(off));
        assertFalse(offAdapter.available());
        assertFalse(offAdapter.send(30L, "t", "c", "biz", "1"), "未启用应降级站内返回 false");
    }

    @Test
    @DisplayName("userid 解析优先级：静态映射 > 通讯录绑定表（resolver SPI，INT2）> 机器人兜底")
    void resolverPrecedence() {
        DingTalkConfig config = workConfig();
        config.setWebhook("http://127.0.0.1:" + port + "/robot/send");
        config.setWebhookSecret(SECRET);
        DingTalkClient client = newClient(config);
        DingTalkNotifyAdapter adapter = new DingTalkNotifyAdapter(config, client);
        // 静态映射含 30，不含 31；resolver 31→dtBound、99→null
        config.setUserMapping("30:dtStatic");
        adapter.setUserResolver(new com.projlife.common.spi.DingtalkUserResolver() {
            @Override
            public String resolveBySysUserId(Long sysUserId) {
                return sysUserId == 31L ? "dtBound" : null;
            }

            @Override
            public String resolveUnionIdBySysUserId(Long sysUserId) {
                return null;
            }
        });

        // 30 走静态映射（发送 userid=dtStatic）
        CAPTURED.remove("async_body");
        assertTrue(adapter.send(30L, "t", "c", "biz", "1"));
        assertTrue(readJson(CAPTURED.get("async_body")).get("userid_list").toString().contains("dtStatic"),
                "静态映射应优先");

        // 31 走绑定表 resolver
        CAPTURED.remove("async_body");
        assertTrue(adapter.send(31L, "t", "c", "biz", "1"));
        assertTrue(readJson(CAPTURED.get("async_body")).get("userid_list").toString().contains("dtBound"),
                "resolver 应命中绑定表");

        // 99 无绑定 → 机器人兜底
        ROBOT_HITS.set(0);
        assertTrue(adapter.send(99L, "t", "c", "biz", "1"), "无绑定应降级机器人且成功");
        assertEquals(1, ROBOT_HITS.get());
    }

    @Test
    @DisplayName("user-mapping 静态映射解析（A-39）")
    void userMapping() {
        DingTalkConfig config = workConfig();
        config.setUserMapping("30:dtu001, 31:dtu002 ,bad,32");
        Map<Long, String> map = config.parseUserMapping();
        assertEquals(2, map.size(), "非法项应跳过");
        assertEquals("dtu001", map.get(30L));
        assertEquals("dtu002", map.get(31L));
    }

    // ---- 桩工具 ----

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String json) {
        try {
            return OM.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("桩捕获报文非法：" + json, ex);
        }
    }
}
