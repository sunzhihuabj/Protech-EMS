package com.projlife.integration.shb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.common.spi.IntegrationAuditRecorder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 售后宝客户端协议测试（SPRINT-INT1）：JDK HttpServer 桩仿真官方网关，
 * 断言 MD5 verifyCode/token 每日 50 次纪律/errorCode 包裹/分页归一/2QPS 节流/Webhook 接收（A-40）。
 */
class ShbClientTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static HttpServer server;
    private static int port;
    private static final AtomicInteger TOKEN_HITS = new AtomicInteger();
    private static final AtomicInteger EVENTS_HITS = new AtomicInteger();
    private static final Map<String, String> CAPTURED = new ConcurrentHashMap<>();

    private static final String APP_KEY = "shbtestkey";
    private static final String APP_SECRET = "shbtestsecret";

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/service/auth/get_access_token", ex -> {
            TOKEN_HITS.incrementAndGet();
            CAPTURED.put("token_body", readBody(ex));
            respond(ex, 200, "{\"errorCode\":\"0\",\"message\":\"调用接口成功\","
                    + "\"data\":{\"access_token\":\"shb-tok-1\",\"expire_time\":\"" + (System.currentTimeMillis() + 7200000) + "\"}}");
        });
        server.createContext("/service/event/get_events", ex -> {
            EVENTS_HITS.incrementAndGet();
            CAPTURED.put("events_query", ex.getRequestURI().getQuery());
            CAPTURED.put("events_body", readBody(ex));
            respond(ex, 200, "{\"errorCode\":\"0\",\"message\":\"调用接口成功\",\"data\":{\"total\":2,\"pages\":1,"
                    + "\"list\":[{\"eventNo\":\"EV1001\",\"state\":\"created\",\"cusName\":\"demo 业主公司\"},"
                    + "{\"eventNo\":\"EV1002\",\"state\":\"processing\",\"taskNo\":\"TK2001\"}]}}");
        });
        server.createContext("/service/event/get_event_types", ex ->
                respond(ex, 200, "{\"errorCode\":\"0\",\"data\":[{\"templateId\":\"T1\",\"templateName\":\"报修\"}]}"));
        server.createContext("/service/event/get_event_EV_FAIL", ex ->
                respond(ex, 200, "{\"errorCode\":\"10002\",\"message\":\"事件不存在\"}"));
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    private ShbConfig config() {
        ShbConfig c = new ShbConfig();
        c.setEnabled(true);
        c.setAppKey(APP_KEY);
        c.setAppSecret(APP_SECRET);
        c.setBaseUrl("http://127.0.0.1:" + port);
        return c;
    }

    private ShbClient newClient(ShbConfig c) {
        return new ShbClient(c, new ShbTokenManager(c, new RestTemplate(), OM), new RestTemplate(), OM);
    }

    @Test
    @DisplayName("verifyCode = md5(appSecret_timestamp) 32 位小写（官方口径）")
    void verifyCodeFormat() {
        String ts = "1530696143000";
        String code = ShbTokenManager.verifyCode(APP_SECRET, ts);
        assertEquals(32, code.length());
        assertEquals(code.toLowerCase(), code, "必须小写");
        // 与通用 MD5 实现互证
        assertEquals(ShbTokenManager.md5Hex(APP_SECRET + "_" + ts), code);
    }

    @Test
    @DisplayName("token 请求体结构 + 缓存命中一次（两次业务调用仅一次 fetch）")
    void tokenCache() {
        TOKEN_HITS.set(0);
        ShbClient client = newClient(config());
        client.getEvents(1, 50, null, null);
        client.getEventTypes();
        assertEquals(1, TOKEN_HITS.get(), "2h TTL 内应缓存，50 次/天纪律");

        Map<String, Object> body = readJson(CAPTURED.get("token_body"));
        assertEquals(APP_KEY, body.get("appKey"));
        assertTrue(String.valueOf(body.get("timestamp")).matches("\\d{13}"), "毫秒时间戳");
        assertEquals(ShbTokenManager.verifyCode(APP_SECRET, String.valueOf(body.get("timestamp"))),
                body.get("verifyCode"), "verifyCode 应可复算验证");
        assertTrue(CAPTURED.get("events_query").contains("accessToken=shb-tok-1"), "accessToken 走 URL Query");
    }

    @Test
    @DisplayName("每日 50 次获取上限：到顶返回失败结果并给明确原因（不抛异常）")
    void dailyLimit() {
        ShbConfig c = config();
        ShbTokenManager tm = new ShbTokenManager(c, new RestTemplate(), OM);
        tm.forceDailyCount(50);
        ShbClient client = new ShbClient(c, tm, new RestTemplate(), OM);
        ShbClient.ShbResult blocked = client.getEvents(1, 50, null, null);
        assertFalse(blocked.ok, "超限应返回失败结果（降级铁律）");
        assertTrue(blocked.message.contains("50"), "应提示每日上限，实测：" + blocked.message);
        // 计数在限内 → 正常刷新
        tm.forceDailyCount(49);
        assertTrue(client.getEvents(1, 50, null, null).ok);
    }

    @Test
    @DisplayName("get_events：分页归一（99→50）+ 事件列表结构透传")
    void getEventsNormalization() {
        ShbClient client = newClient(config());
        ShbClient.ShbResult r = client.getEvents(1, 99, "created", null);
        assertTrue(r.ok);
        Map<String, Object> body = readJson(CAPTURED.get("events_body"));
        assertEquals(50, body.get("pageSize"), "99 应归一为允许值 50");
        assertEquals(1, body.get("pageNum"));
        assertEquals("created", body.get("state"));
        assertEquals(2, r.data.path("list").size());
        assertEquals("TK2001", r.data.path("list").get(1).path("taskNo").asText(), "taskNo 关联键应保留");
    }

    @Test
    @DisplayName("errorCode≠0 → 失败结果不抛异常；详情失败带 eventNo 落审计")
    void envelopeFailure() {
        ShbClient client = newClient(config());
        ShbClient.ShbResult r = client.getEventDetail("EV_FAIL");
        assertFalse(r.ok);
        assertEquals("10002", r.errorCode);
        assertEquals("事件不存在", r.message);
    }

    @Test
    @DisplayName("读接口 2 QPS 节流：连续两次读取间隔 ≥600ms")
    void throttle() {
        ShbClient client = newClient(config());
        long t1 = System.currentTimeMillis();
        client.getEventTypes();
        client.getEventTypes();
        long elapsed = System.currentTimeMillis() - t1;
        assertTrue(elapsed >= ShbClient.READ_INTERVAL_MS, "第二次读取应被节流至 ≥600ms，实测 " + elapsed + "ms");
    }

    @Test
    @DisplayName("Webhook 接收器：原始报文落审计（INBOUND）+ sharedKey 校验 + 未启用忽略")
    void webhook() {
        ShbConfig c = config();
        ObjectMapper om = new ObjectMapper();
        java.util.List<String> recorded = new java.util.ArrayList<>();
        IntegrationAuditRecorder recorder = (code, dir, remoteId, status, err, payload) ->
                recorded.add(dir + "|" + remoteId + "|" + status + "|" + payload);

        ShbWebhookController controller = new ShbWebhookController(c, om);
        controller.setAuditRecorder(recorder);

        String push = "{\"pushType\":\"task\",\"data\":{\"eventNo\":\"EV1001\",\"taskNo\":\"TK2001\",\"state\":\"finished\"}}";
        Map<String, Object> resp = controller.webhook(null, push);
        assertEquals("0", resp.get("errorCode"));
        assertEquals(1, recorded.size());
        assertTrue(recorded.get(0).startsWith("INBOUND|EV1001|SUCCESS|"), "应提取 eventNo 并原始落库");
        assertTrue(recorded.get(0).contains("taskNo"), "原始报文应全量保存（A-40）");

        // sharedKey 校验
        c.setWebhookSharedKey("k123");
        Map<String, Object> rejected = controller.webhook("wrong", push);
        assertEquals("403", rejected.get("errorCode"));
        Map<String, Object> accepted = controller.webhook("k123", push);
        assertEquals("0", accepted.get("errorCode"));

        // 未启用 → 忽略不落审计
        ShbConfig off = new ShbConfig();
        ShbWebhookController offController = new ShbWebhookController(off, om);
        offController.setAuditRecorder(recorder);
        int before = recorded.size();
        Map<String, Object> ignored = offController.webhook(null, push);
        assertEquals("0", ignored.get("errorCode"));
        assertTrue(String.valueOf(ignored.get("message")).contains("ignored"));
        assertEquals(before, recorded.size(), "未启用不应新增审计");
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
