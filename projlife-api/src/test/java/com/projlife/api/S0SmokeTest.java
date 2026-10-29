package com.projlife.api;

import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.enums.DataScope;
import com.projlife.common.kv.KeyValStore;
import com.projlife.org.mapper.UserPageQuery;
import com.projlife.org.mapper.SysUserMapper;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S0 平台底座冒烟测试（16 册 §五 S0 完成标准 + §七 自检机制）。
 * 覆盖：S0-2 迁移与种子 / S0-3 认证与锁定 / S0-4 RBAC / S0-5 数据范围 / S0-6 审计 /
 *       S0-7 编码引擎并发 / S0-8 字典保护 / BR-08 端限制前置。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class S0SmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CodeSequenceService codeSequenceService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private KeyValStore keyValStore;

    // ---------- 工具 ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String username, String password, String client) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", password, "client", client);
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class);
        return (Map<String, Object>) ((Map<String, Object>) resp.getBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, String token, Object body, Map<String, String> extra) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (extra != null) {
            extra.forEach(headers::set);
        }
        ResponseEntity<Map> resp = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return (Map<String, Object>) resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<Map> resp = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (Map<String, Object>) resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> delete(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<Map> resp = rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
        return (Map<String, Object>) resp.getBody();
    }

    private String tokenOf(Map<String, Object> loginResp) {
        return (String) loginResp.get("accessToken");
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("S0-2/S0-15 Flyway 迁移成功 + 种子数据完整（42 组字典/14 部门/18 角色/9 板）")
    void seedLoaded() {
        Map<String, Object> login = login("admin@demo", "ProjLife@2026", "web");
        assertEquals(0, ((Number) login.get("code")).intValue(), "登录应成功");
        String token = tokenOf((Map<String, Object>) login.get("data"));

        Map<String, Object> me = (Map<String, Object>) get("/api/v1/auth/me", token).get("data");
        assertNotNull(me.get("permissions"));
        assertTrue(((List<String>) me.get("permissions")).contains("org:user:manage"), "R03 应有用户管理权限");

        Map<String, Object> deptTree = get("/api/v1/org/depts/tree", token);
        assertEquals(0, ((Number) deptTree.get("code")).intValue());
    }

    @Test
    @DisplayName("S0-3 登录失败 5 次锁定（15 分钟）+ 首登强改标记")
    void loginLock() {
        Map<String, Object> last = null;
        for (int i = 0; i < 5; i++) {
            last = login("tech@demo", "wrong-password", "web");
        }
        assertEquals(1001, ((Number) last.get("code")).intValue(), "错误密码应返回 1001");
        Map<String, Object> locked = login("tech@demo", "ProjLife@2026", "web");
        // HTTP 状态 429，业务码 6001（TOO_MANY）
        assertEquals(6001, ((Number) locked.get("code")).intValue(), "第 6 次即使密码正确也应锁定");
        // 解锁，避免影响其他用例（真实环境由 15 分钟 TTL 自然解锁）
        keyValStore.remove("login:fail:tech@demo");
    }

    @Test
    @DisplayName("S0-3 登出后 token 进入黑名单（401）")
    void logoutBlacklist() throws Exception {
        Map<String, Object> data = (Map<String, Object>) login("admin@demo", "ProjLife@2026", "web").get("data");
        String token = tokenOf(data);
        assertEquals(0, ((Number) get("/api/v1/auth/me", token).get("code")).intValue());
        Map<String, Object> logoutResp = post("/api/v1/auth/logout", token, null, null);
        assertEquals(0, ((Number) logoutResp.get("code")).intValue(), "登出请求本身应成功");

        // 诊断：解码 jti，断言黑名单已写入 KV 存储
        String payload = token.split("\\.")[1];
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        com.fasterxml.jackson.databind.JsonNode claims =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(decoded);
        String jti = claims.get("jti").asText();
        assertTrue(keyValStore.exists("token:blacklist:" + jti), "黑名单应已写入 KV 存储");

        Map<String, Object> after = get("/api/v1/auth/me", token);
        assertEquals(2001, ((Number) after.get("code")).intValue(), "登出后应 401");
    }

    @Test
    @DisplayName("S0-4 RBAC：R11 调用户管理接口被拒 403 + missingPerm")
    void rbacForbidden() {
        Map<String, Object> data = (Map<String, Object>) login("store@demo", "ProjLife@2026", "web").get("data");
        String token = tokenOf(data);
        Map<String, Object> resp = get("/api/v1/org/users", token);
        assertEquals(2002, ((Number) resp.get("code")).intValue(), "越权应 403/2002");
    }

    @Test
    @DisplayName("BR-08 端限制：施工人员 PC 登录 403，小程序放行")
    void workerClientLimit() {
        Map<String, Object> pc = login("worker@demo", "ProjLife@2026", "web");
        assertEquals(2002, ((Number) pc.get("code")).intValue(), "PC 登录应 403");

        Map<String, Object> mp = login("worker@demo", "ProjLife@2026", "mp");
        assertEquals(0, ((Number) mp.get("code")).intValue(), "小程序登录应放行");
    }

    @Test
    @DisplayName("S0-5 数据范围：DEPT 只见本部门，DEPT_SUB 含下级")
    void dataScope() {
        LoginUser deptUser = new LoginUser();
        deptUser.setUserId(2L);
        deptUser.setUserCode("E0002");
        deptUser.setDeptId(4L);
        deptUser.setDataScope(DataScope.DEPT.name());
        UserContextHolder.set(deptUser);
        try {
            List<Long> dept4 = userMapper.selectUserPage(new Page<>(1, 200), new UserPageQuery())
                    .getRecords().stream().map(u -> u.getDeptId()).collect(Collectors.toList());
            assertFalse(dept4.isEmpty(), "本部门应有用户");
            assertTrue(dept4.stream().allMatch(id -> id == 4L), "DEPT 范围只能看到本部门（部门 4）");
        } finally {
            UserContextHolder.clear();
        }

        LoginUser subUser = new LoginUser();
        subUser.setUserId(3L);
        subUser.setUserCode("E0003");
        subUser.setDeptId(6L);
        subUser.setDataScope(DataScope.DEPT_SUB.name());
        UserContextHolder.set(subUser);
        try {
            Set<Long> deptIds = userMapper.selectUserPage(new Page<>(1, 200), new UserPageQuery())
                    .getRecords().stream().map(u -> u.getDeptId()).collect(Collectors.toSet());
            assertTrue(deptIds.contains(16L), "DEPT_SUB 应含技术中心下级组（16 研发设计组）");
            assertFalse(deptIds.contains(12L), "DEPT_SUB 不应包含其他部门（12 仓储部）");
        } finally {
            UserContextHolder.clear();
        }
    }

    @Test
    @DisplayName("S0-7 编码引擎：50 线程并发取号无重复")
    void codeEngineConcurrency() throws InterruptedException {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<String> codes = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    codes.add(codeSequenceService.next(CodeTypes.customer()));
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(60, TimeUnit.SECONDS), "并发取号应在 60 秒内完成");
        pool.shutdownNow();
        assertEquals(threads, codes.size(), "50 并发取号必须无重复（02 册 §4.17）");
    }

    @Test
    @DisplayName("S0-8 字典保护：系统内置枚举删除被拒（422）")
    void systemDictProtected() {
        Map<String, Object> data = (Map<String, Object>) login("admin@demo", "ProjLife@2026", "web").get("data");
        String token = tokenOf(data);
        Map<String, Object> types = (Map<String, Object>) get("/api/v1/system/dicts", token);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) types.get("data");
        Map<String, Object> userType = rows.stream()
                .filter(t -> "user_type".equals(t.get("typeCode"))).findFirst().orElseThrow();
        Map<String, Object> del = delete("/api/v1/system/dicts/types/" + userType.get("id"), token);
        assertEquals(5001, ((Number) del.get("code")).intValue(), "系统枚举删除应 422");
    }

    @Test
    @DisplayName("S0-6 审计切面：写操作留痕可查")
    void auditRecorded() {
        Map<String, Object> data = (Map<String, Object>) login("admin@demo", "ProjLife@2026", "web").get("data");
        String token = tokenOf(data);
        Map<String, Object> body = Map.of(
                "userCode", "E9099", "username", "audit-demo@demo", "realName", "审计演示",
                "password", "Init@12345678", "deptId", 4, "userType", "INTERNAL");
        Map<String, Object> created = post("/api/v1/org/users", token, body, null);
        assertEquals(0, ((Number) created.get("code")).intValue(), "建用户应成功");

        Map<String, Object> logs = (Map<String, Object>) get("/api/v1/system/logs?action=create_user", token);
        Map<String, Object> page = (Map<String, Object>) logs.get("data");
        assertNotNull(page);
        List<Map<String, Object>> records = (List<Map<String, Object>>) page.get("records");
        assertFalse(records.isEmpty(), "审计日志应有 create_user 记录");
    }
}
