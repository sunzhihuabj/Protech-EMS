package com.projlife.integration.tplus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T+ 客户端协议测试（SPRINT-INT1）：JDK HttpServer 桩仿真 T+ v2 网关。
 * 断言：Authorization=Base64(JSON 四字段) / authInfo JWT 三段+RSA 验签互证 / RealName 请求体 /
 * EXSM0001 清 token 重试 / 13.0 红线（无 Asyn）/ 缺补丁提示 / 实例探测口径。
 */
class TplusClientTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static HttpServer server;
    private static int port;
    private static final AtomicInteger TOKEN_HITS = new AtomicInteger();
    private static final AtomicInteger INVENTORY_HITS = new AtomicInteger();
    private static final Map<String, String> CAPTURED = new ConcurrentHashMap<>();

    private static KeyPair keyPair;

    @BeforeAll
    static void startStub() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/tplus/api/v2/collaborationapp/GetRealNameTPlusToken", ex -> {
            TOKEN_HITS.incrementAndGet();
            CAPTURED.put("token_query", ex.getRequestURI().getQuery());
            CAPTURED.put("token_body", readBody(ex));
            respond(ex, 200, "{\"access_token\":\"tplus-tok-1\"}");
        });
        server.createContext("/tplus/api/v2/Inventory/Query", ex -> {
            CAPTURED.put("inv_auth", ex.getRequestHeaders().getFirst("Authorization"));
            CAPTURED.put("inv_body", readBody(ex));
            // 第一次 EXSM0001（模拟 token 失效）→ 触发客户端清缓存重试；之后成功
            if (INVENTORY_HITS.incrementAndGet() == 1) {
                respond(ex, 999, "{\"code\":\"EXSM0001\",\"message\":\"请提供认证信息\"}");
            } else {
                respond(ex, 200, "{\"code\":\"0\",\"message\":\"\",\"data\":{\"RecordCount\":2,\"Record\":[{\"Code\":\"WL001\",\"Name\":\"demo 风机叶片\"}]}}");
            }
        });
        server.createContext("/tplus/api/v2/Partner/Query", ex -> {
            CAPTURED.put("partner_body", readBody(ex));
            respond(ex, 999, "{\"code\":\"EXSM0101\",\"message\":\"提供的服务名称(Partner/Query)不正确\"}");
        });
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    private TplusConfig config() {
        TplusConfig c = new TplusConfig();
        c.setEnabled(true);
        c.setBaseUrl("http://127.0.0.1:" + port);
        c.setAppKey("tplus-appkey");
        c.setAppSecret("tplus-appsecret");
        c.setOrgId("org-9527");
        c.setTokenMode("REALNAME");
        c.setUserName("admin");
        c.setPassword("secret-pass");
        c.setAccNum("001");
        c.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        return c;
    }

    private TplusClient newClient(TplusConfig c) {
        return new TplusClient(c, new TplusTokenManager(c, new RestTemplate(), OM),
                new TplusJwtSigner(), new RestTemplate(), OM);
    }

    @Test
    @DisplayName("Authorization 头：Base64(JSON{appKey,authInfo,orgId,access_token}) 四字段 + JWT 三段可验签")
    void authorizationStructure() throws Exception {
        TOKEN_HITS.set(0);
        INVENTORY_HITS.set(0);
        TplusClient client = newClient(config());
        // 第一次 EXSM0001 → 自动重试成功
        TplusClient.TplusResult r = client.queryInventory(new HashMap<>());
        assertTrue(r.ok, "EXSM0001 应触发清缓存重试后成功，实测：" + r.code + " " + r.message);
        assertEquals(2, TOKEN_HITS.get(), "重试应重取 token");

        // 解码 Authorization
        String decoded = new String(Base64.getDecoder().decode(CAPTURED.get("inv_auth")), StandardCharsets.UTF_8);
        JsonNode auth = OM.readTree(decoded);
        assertEquals("tplus-appkey", auth.path("appKey").asText());
        assertEquals("org-9527", auth.path("orgId").asText());
        assertEquals("tplus-tok-1", auth.path("access_token").asText());
        String authInfo = auth.path("authInfo").asText();

        // JWT 三段结构 + RSA 验签（与 TplusJwtSigner 互证）
        String[] parts = authInfo.split("\\.");
        assertEquals(3, parts.length, "JWT 应为 header.payload.signature");
        String signingInput = parts[0] + "." + parts[1];
        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(sig), "authInfo JWT 签名应可用测试私钥对的公钥验签通过");

        JsonNode payload = OM.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        assertEquals("tplus-appkey", payload.path("appkey").asText());
        assertEquals("org-9527", payload.path("orgid").asText());
    }

    @Test
    @DisplayName("RealName token：IsFree=1 + _args{userName,password,accNum}")
    void realNameToken() {
        assertEquals("IsFree=1", CAPTURED.get("token_query"));
        JsonNode body = readJsonNode(CAPTURED.get("token_body"));
        assertEquals("admin", body.path("_args").path("userName").asText());
        assertEquals("001", body.path("_args").path("accNum").asText());
    }

    @Test
    @DisplayName("13.0 红线：业务请求体仅 _args，不含 type=Asyn（18.0+ 特性）")
    void noAsyncField() {
        JsonNode body = readJsonNode(CAPTURED.get("inv_body"));
        assertTrue(body.has("_args"));
        assertFalse(body.has("type"), "13.0 不支持 type=Asyn");
    }

    @Test
    @DisplayName("缺补丁/服务名错误 → 失败结果带 13.0 提示，不抛异常")
    void patchHint() {
        TplusClient client = newClient(config());
        TplusClient.TplusResult r = client.call("Partner", "Query", new HashMap<>());
        assertFalse(r.ok);
        assertTrue(r.message.contains("13.0"), "应内置补丁版本提示，实测：" + r.message);
    }

    @Test
    @DisplayName("实例探测：有 HTTP 响应即 UP；不可达 DOWN 不抛异常")
    void probe() {
        TplusClient client = newClient(config());
        TplusClient.TplusResult up = client.probe();
        assertTrue(up.ok, "桩首页存在应 UP");

        TplusConfig dead = config();
        dead.setBaseUrl("http://127.0.0.1:1");
        TplusClient.TplusResult down = newClient(dead).probe();
        assertFalse(down.ok);
        assertEquals("UNREACHABLE", down.code);
    }

    @Test
    @DisplayName("未配置 → NOT_CONFIGURED；私钥缺失 → 显式报错不降级")
    void guardRails() {
        TplusConfig off = new TplusConfig();
        assertFalse(off.configured());
        TplusClient.TplusResult r = newClient(off).queryInventory(new HashMap<>());
        assertFalse(r.ok);
        assertEquals("NOT_CONFIGURED", r.code);

        TplusConfig noKey = config();
        noKey.setPrivateKey("");
        TplusClient noKeyClient = newClient(noKey);
        TplusClient.TplusResult noKeyResult = noKeyClient.queryInventory(new HashMap<>());
        assertFalse(noKeyResult.ok);
        assertEquals("NO_PRIVATE_KEY", noKeyResult.code, "私钥缺失必须显式失败，不得发未签名请求");
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

    private static JsonNode readJsonNode(String json) {
        try {
            return OM.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("桩捕获报文非法：" + json, ex);
        }
    }
}
