package com.projlife.org.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.integration.notify.DingTalkClient;
import com.projlife.integration.notify.DingTalkConfig;
import com.projlife.integration.notify.DingTalkTokenManager;
import com.projlife.org.entity.SysDingtalkBinding;
import com.projlife.org.entity.SysUser;
import com.projlife.org.mapper.SysDingtalkBindingMapper;
import com.projlife.org.mapper.SysUserMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉钉通讯录同步测试（SPRINT-INT2）：JDK HttpServer 桩仿真官方端点 + Mockito 内存 Mapper。
 * 断言：部门树 BFS / 用户分页 cursor / 跨部门去重 / MOBILE→NAME(唯一)→NONE 匹配（A-44）/
 * MANUAL 不被同步覆盖 / 新版 Header 鉴权 + 旧版 Query 鉴权。
 */
class DingTalkDirectorySyncTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static HttpServer server;
    private static int port;
    private static final Map<String, String> CAPTURED = new ConcurrentHashMap<>();
    private static final AtomicInteger TOKEN_HITS = new AtomicInteger();

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/v1.0/oauth2/accessToken", ex -> {
            TOKEN_HITS.incrementAndGet();
            respond(ex, 200, "{\"accessToken\":\"tok-dir-1\",\"expireIn\":7200}");
        });
        server.createContext("/v1.0/departments/1/subDepts", ex -> {
            try {
                CAPTURED.put("dept1_token", String.valueOf(ex.getRequestHeaders().getFirst("x-acs-dingtalk-access-token")));
                respond(ex, 200, "{\"result\":[{\"dept_id\":10,\"name\":\"工程部\"},{\"dept_id\":20,\"name\":\"销售部\"}]}");
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        server.createContext("/v1.0/departments/10/subDepts", ex ->
                respond(ex, 200, "{\"result\":[{\"dept_id\":11,\"name\":\"安装组\"}]}"));
        server.createContext("/v1.0/departments/11/subDepts", ex -> respond(ex, 200, "{\"result\":[]}"));
        server.createContext("/v1.0/departments/20/subDepts", ex -> respond(ex, 200, "{\"result\":[]}"));
        server.createContext("/topapi/v2/user/list", ex -> {
            String body = readBody(ex);
            CAPTURED.put("userlist_body_" + System.nanoTime(), body);
            JsonNode root = OM.readTree(body);
            long cursor = root.path("cursor").asLong(0);
            if (cursor == 0) {
                // 第一页：张三（手机号匹配）/ 王五（重名，出现在两部门——此页仅一次）
                respond(ex, 200, "{\"errcode\":0,\"result\":{\"has_more\":true,\"next_cursor\":100,\"list\":["
                        + "{\"userid\":\"dtA\",\"name\":\"张三\",\"unionid\":\"uniA\",\"mobile\":\"13800000001\"},"
                        + "{\"userid\":\"dtC\",\"name\":\"王五\",\"unionid\":\"uniC\"}]}}");
            } else {
                // 第二页：李四（唯一姓名匹配）
                respond(ex, 200, "{\"errcode\":0,\"result\":{\"has_more\":false,\"list\":["
                        + "{\"userid\":\"dtB\",\"name\":\"李四\",\"unionid\":\"uniB\"}]}}");
            }
        });
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    // ---- 测试数据 ----

    private SysUser sysUser(long id, String realName, String phone) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setRealName(realName);
        u.setPhone(phone);
        u.setDeleted(0);
        return u;
    }

    private DingTalkConfig config() {
        DingTalkConfig c = new DingTalkConfig();
        c.setEnabled(true);
        c.setAppKey("ding-key");
        c.setAppSecret("ding-secret");
        c.setAgentId("110001");
        c.setApiBase("http://127.0.0.1:" + port);
        c.setOapiBase("http://127.0.0.1:" + port);
        return c;
    }

    private DingTalkClient client(DingTalkConfig c) {
        return new DingTalkClient(c, new DingTalkTokenManager(c, new RestTemplate(), OM),
                new RestTemplate(), OM);
    }

    @Test
    @DisplayName("全量同步：部门树 BFS + 用户分页 + 去重 + MOBILE/NAME/NONE 匹配 + Header/Query 鉴权")
    void fullSync() {
        TOKEN_HITS.set(0);
        DingTalkConfig c = config();
        DingTalkClient client = client(c);

        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        when(userMapper.selectList(any())).thenReturn(java.util.Arrays.asList(
                sysUser(5L, "张三", "13800000001"),
                sysUser(6L, "李四", null),
                sysUser(7L, "王五", null),
                sysUser(8L, "王五", null)));
        SysDingtalkBindingMapper bindingMapper = Mockito.mock(SysDingtalkBindingMapper.class);

        DingTalkDirectorySyncService service = new DingTalkDirectorySyncService(c, client, bindingMapper, userMapper);
        DingTalkDirectorySyncService.SyncSummary s = service.syncAll();

        assertTrue(s.ok, "同步应成功：" + s.reason);
        assertEquals(4, s.depts, "根 1 + 工程部 10 + 销售部 20 + 安装组 11");
        assertEquals(3, s.users, "3 个钉钉用户（分页两页）");
        assertEquals(2, s.bound, "MOBILE 张三 + NAME 李四");
        assertTrue(s.unmatchedNames.contains("王五"), "重名应 NONE 且进未匹配清单");

        // 实体断言：insert 3 次全部创建
        org.mockito.ArgumentCaptor<SysDingtalkBinding> captor =
                org.mockito.ArgumentCaptor.forClass(SysDingtalkBinding.class);
        verify(bindingMapper, times(3)).insert(captor.capture());
        Map<String, SysDingtalkBinding> byId = new ConcurrentHashMap<>();
        for (SysDingtalkBinding b : captor.getAllValues()) {
            byId.put(b.getDingtalkUserid(), b);
        }
        assertEquals(5L, byId.get("dtA").getSysUserId());
        assertEquals("MOBILE", byId.get("dtA").getMatchType());
        assertEquals("uniA", byId.get("dtA").getUnionId());
        assertEquals(6L, byId.get("dtB").getSysUserId());
        assertEquals("NAME", byId.get("dtB").getMatchType());
        assertNull(byId.get("dtC").getSysUserId());
        assertEquals("NONE", byId.get("dtC").getMatchType());
        assertNotNull(byId.get("dtA").getSyncedAt());

        // 鉴权口径：新 REST Header + 旧 topapi Query
        assertEquals("tok-dir-1", CAPTURED.get("dept1_token"));
        assertTrue(TOKEN_HITS.get() >= 1);
        // 分页：has_more 驱动过 cursor=100 的第二页请求（静态桩被多测试共享，断言存在性而非次数）
        boolean secondPage = CAPTURED.entrySet().stream()
                .filter(e -> e.getKey().startsWith("userlist_body_"))
                .anyMatch(e -> e.getValue().contains("\"cursor\":100"));
        assertTrue(secondPage, "has_more 应驱动第二页");
    }

    @Test
    @DisplayName("MANUAL 绑定不被同步覆盖（A-44）")
    void manualKept() {
        DingTalkConfig c = config();
        DingTalkClient client = client(c);
        SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
        when(userMapper.selectList(any())).thenReturn(java.util.Collections.singletonList(sysUser(7L, "王五", null)));
        SysDingtalkBindingMapper bindingMapper = Mockito.mock(SysDingtalkBindingMapper.class);

        DingTalkDirectorySyncService service = new DingTalkDirectorySyncService(c, client, bindingMapper, userMapper) {
            @Override
            protected SysDingtalkBinding findExisting(String userid) {
                SysDingtalkBinding manual = new SysDingtalkBinding();
                manual.setDingtalkUserid(userid);
                manual.setSysUserId(7L);
                manual.setMatchType("MANUAL");
                return manual;
            }
        };
        DingTalkDirectorySyncService.SyncSummary s = service.syncAll();
        assertTrue(s.ok);
        assertEquals(s.users, s.manualKept, "全部 MANUAL 绑定应保留（" + s.users + " 个）");
        org.mockito.ArgumentCaptor<SysDingtalkBinding> captor =
                org.mockito.ArgumentCaptor.forClass(SysDingtalkBinding.class);
        verify(bindingMapper, Mockito.times(s.users)).updateById(captor.capture());
        for (SysDingtalkBinding b : captor.getAllValues()) {
            assertEquals(7L, b.getSysUserId(), "MANUAL 的 sys_user_id 不应被匹配结果覆盖");
            assertEquals("MANUAL", b.getMatchType(), "MANUAL 标记不应被覆盖");
        }
        verify(bindingMapper, Mockito.never()).insert(any());
    }

    @Test
    @DisplayName("匹配规则单测：手机号优先于姓名；重名不匹配")
    void matchRules() {
        DingTalkDirectorySyncService service = new DingTalkDirectorySyncService(
                config(), client(config()),
                Mockito.mock(SysDingtalkBindingMapper.class), Mockito.mock(SysUserMapper.class));
        List<SysUser> users = java.util.Arrays.asList(
                sysUser(1L, "李四", "13800000002"),
                sysUser(2L, "王五", "13900000000"),
                sysUser(3L, "王五", null));

        // 手机号命中即返回（即使姓名也对得上别人）
        SysDingtalkBinding b1 = new SysDingtalkBinding();
        service.match(b1, read("{\"mobile\":\"13800000002\",\"name\":\"王五\"}"), users);
        assertEquals(1L, b1.getSysUserId());
        assertEquals("MOBILE", b1.getMatchType());

        // 唯一姓名
        SysDingtalkBinding b2 = new SysDingtalkBinding();
        service.match(b2, read("{\"name\":\"李四\"}"), users);
        assertEquals(1L, b2.getSysUserId());
        assertEquals("NAME", b2.getMatchType());

        // 重名 → NONE
        SysDingtalkBinding b3 = new SysDingtalkBinding();
        service.match(b3, read("{\"name\":\"王五\"}"), users);
        assertNull(b3.getSysUserId());
        assertEquals("NONE", b3.getMatchType());
    }

    // ---- 桩工具 ----

    private static JsonNode read(String json) {
        try {
            return OM.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

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
}
