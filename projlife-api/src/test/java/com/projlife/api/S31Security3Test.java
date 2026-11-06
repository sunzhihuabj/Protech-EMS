package com.projlife.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.project.entity.PrjMember;
import com.projlife.project.mapper.PrjMemberMapper;
import com.projlife.security.auth.DataScopeGuard;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SPRINT-SEC3 安全专项 E2E（审核 P0/P1 收口）：
 * SEC3-2 JWT typ 校验（refresh 令牌不得当访问令牌用）/
 * SEC3-3 refresh rotation（旧刷新令牌一次性）/
 * SEC3-5 按 ID 直查数据范围（SELF/DEPT 档越界 403）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S31Security3Test {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DataScopeGuard dataScopeGuard;

    @Autowired
    private PrjMemberMapper memberMapper;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    private static String accessToken;
    private static String refreshToken;
    private static Long contractId;

    private static final class Resp {
        final Map<String, Object> body;

        Resp(Map<String, Object> body) { this.body = body; }

        int code() {
            Object code = body.get("code");
            if (code == null) {
                throw new AssertionError("非标准响应: " + body);
            }
            return ((Number) code).intValue();
        }

        @SuppressWarnings("unchecked")
        <T> T data() { return (T) body.get("data"); }

        Map<String, Object> body() { return body; }
    }

    /**
     * POST 统一走 JDK HttpClient：TestRestTemplate（JDK HttpURLConnection）对 4xx+POST 有
     * HttpRetryException（streaming mode 认证重试）怪癖，拿不到真实响应体（SPRINT-SEC3 实测）。
     */
    private Resp post(String path, String bearer, Object body) {
        try {
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                            new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)));
            if (bearer != null) {
                builder.header("Authorization", "Bearer " + bearer);
            }
            java.net.http.HttpResponse<String> raw = java.net.http.HttpClient.newHttpClient()
                    .send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            return new Resp(new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw.body(), Map.class));
        } catch (Exception ex) {
            throw new IllegalStateException("HTTP 调用失败: " + path, ex);
        }
    }

    private Resp get(String path, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return new Resp(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody());
    }

    private void loginAs(String username) {
        Resp resp = post("/api/v1/auth/login", null,
                Map.of("username", username, "password", "ProjLife@2026", "client", "web"));
        assertEquals(0, resp.code(), "登录应成功: " + username);
        Map<?, ?> data = (Map<?, ?>) resp.data();
        accessToken = (String) data.get("accessToken");
        refreshToken = (String) data.get("refreshToken");
    }

    @Test
    @Order(1)
    @DisplayName("SEC3-2 refresh 令牌当访问令牌使用被拒 401")
    void refreshTokenCannotBeUsedAsAccess() {
        loginAs("biz@demo");
        Resp me = get("/api/v1/auth/me", refreshToken);
        assertEquals(2001, me.code(), "refresh 令牌调业务端点应 401/2001（typ≠access）");

        // 对照组：access 令牌正常
        assertEquals(0, get("/api/v1/auth/me", accessToken).code());
    }

    @Test
    @Order(2)
    @DisplayName("SEC3-3 refresh rotation：旧刷新令牌用后即废，新刷新令牌可继续轮换")
    void refreshRotation() {
        loginAs("biz@demo");
        String oldRefresh = refreshToken;

        Resp first = post("/api/v1/auth/refresh", null, Map.of("refreshToken", oldRefresh));
        assertEquals(0, first.code(), "首次刷新应成功");
        Map<?, ?> data = (Map<?, ?>) first.data();
        String newRefresh = (String) data.get("refreshToken");
        String newAccess = (String) data.get("accessToken");
        assertNotEquals(oldRefresh, newRefresh, "rotation 后必须下发新 refresh 令牌");
        assertEquals(0, get("/api/v1/auth/me", newAccess).code(), "新 access 令牌应可用");

        Resp replay = post("/api/v1/auth/refresh", null, Map.of("refreshToken", oldRefresh));
        assertEquals(2001, replay.code(), "旧 refresh 令牌重放应 401/2001（rotation 入黑名单）");

        Resp second = post("/api/v1/auth/refresh", null, Map.of("refreshToken", newRefresh));
        assertEquals(0, second.code(), "新 refresh 令牌应可继续轮换");
    }

    @Test
    @Order(3)
    @DisplayName("SEC3-5 合同按 ID 直查数据范围：owner/同部门/PROJECT/ALL 放行")
    void contractDetailDataScopePositive() {
        loginAs("biz@demo");
        Map<String, Object> contract = new HashMap<>();
        contract.put("contractName", "SPRINT-SEC3 数据范围演示合同");
        contract.put("nature", "CG");
        contract.put("partyA", "远景能源江苏有限公司（demo）");
        contract.put("partyB", "天立达（demo）");
        contract.put("counterpartyId", 6);
        contract.put("legalEntityId", 2);
        contract.put("businessType", "AZFWXF");
        contract.put("amount", 500000);
        contract.put("taxRate", 0.13);
        contract.put("paymentTerms", "预付 30%，到货 40%，验收 30%");
        contract.put("contact", "王工");
        contract.put("phone", "13900000000");
        contract.put("signDate", LocalDate.now().toString());
        contract.put("effectiveDate", LocalDate.now().toString());
        contract.put("warrantyMonths", 24);
        contract.put("currency", "CNY");
        Resp created = post("/api/v1/contract", accessToken, contract);
        assertEquals(0, created.code(), created.body().toString());
        contractId = Long.valueOf(String.valueOf((Object) created.data()));

        assertEquals(0, get("/api/v1/contract/" + contractId, accessToken).code(), "owner 本人（SELF）应放行");

        loginAs("boss@demo");
        assertEquals(0, get("/api/v1/contract/" + contractId, accessToken).code(),
                "同部门主管（DEPT，部门 4）应放行");
        // PROJECT/ALL 档不在此处 HTTP 级验证：种子账号 R14 无 contract:view、R03/R15 也未授权 46，
        // 会先被 RequirePermission 拦（2002），与本守卫无关；两档行为由 dataScopeGuardUnit 进程内直测覆盖。
    }

    @Test
    @Order(4)
    @DisplayName("SEC3-5 数据范围守卫：SELF/DEPT 跨部门越界 403（进程内直测）")
    void dataScopeGuardUnit() {
        // SELF 档（biz E0002 部门 4）看他人单据 → 拒
        asUser(2L, "E0002", 4L, "SELF", () -> {
            assertThrows(BizException.class, () -> dataScopeGuard.checkByOwner("E0004"), "SELF 看他人单据应拒");
            assertDoesNotThrow(() -> dataScopeGuard.checkByOwner("E0002"), "SELF 看本人单据应放行");
        });

        // DEPT 档（boss E0003 部门 4）跨部门 → 拒；同部门 → 放行
        asUser(3L, "E0003", 4L, "DEPT", () -> {
            assertThrows(BizException.class, () -> dataScopeGuard.checkByOwner("E0004"), "DEPT 跨部门应拒");
            assertDoesNotThrow(() -> dataScopeGuard.checkByOwner("E0002"), "DEPT 同部门应放行");
        });

        // PROJECT / ALL / system 均放行
        asUser(4L, "E0004", 15L, "PROJECT",
                () -> assertDoesNotThrow(() -> dataScopeGuard.checkByOwner("E0002"), "PROJECT 档暂放行（备案）"));
        asUser(1L, "E0001", 2L, "ALL",
                () -> assertDoesNotThrow(() -> dataScopeGuard.checkByOwner("E0004"), "ALL 档放行"));
        asUser(2L, "E0002", 4L, "SELF",
                () -> assertDoesNotThrow(() -> dataScopeGuard.checkByOwner("system"), "系统动作数据放行（备案）"));
    }

    @Test
    @Order(5)
    @DisplayName("SEC3-C PROJECT 档落地：非成员 403 / 在册成员放行 / SELF·DEPT 成员兜底")
    void projectScopeMembership() {
        Long pid = 999901L;
        memberMapper.delete(new LambdaQueryWrapper<PrjMember>().eq(PrjMember::getProjectId, pid));
        try {
            // PROJECT 档：非在册成员 → 拒
            asUser(4L, "E0004", 15L, "PROJECT", () ->
                    assertThrows(BizException.class, () -> dataScopeGuard.checkByProject(pid, "E0002"),
                            "PROJECT 档非在册成员应拒"));

            // PROJECT 档：在册成员 → 放行
            PrjMember member = new PrjMember();
            member.setProjectId(pid);
            member.setUserId(4L);
            member.setRoleCode("MEMBER");
            member.setJoinDate(LocalDate.now());
            member.setIsActive(1);
            memberMapper.insert(member);
            asUser(4L, "E0004", 15L, "PROJECT", () ->
                    assertDoesNotThrow(() -> dataScopeGuard.checkByProject(pid, "E0002"),
                            "PROJECT 档在册成员应放行"));

            // SELF 档：owner 非本人但在册成员 → 兜底放行（FR-PRJ-28 协作成员通道）
            asUser(4L, "E0004", 15L, "SELF", () ->
                    assertDoesNotThrow(() -> dataScopeGuard.checkByProject(pid, "E0002"),
                            "SELF 档在册成员兜底放行"));

            // SELF 档：非本人且非成员 → 拒
            asUser(2L, "E0002", 4L, "SELF", () ->
                    assertThrows(BizException.class, () -> dataScopeGuard.checkByProject(pid, "E0004"),
                            "SELF 档非成员看他人项目应拒"));

            // DEPT 档：同部门 owner → owner 口径放行
            asUser(3L, "E0003", 4L, "DEPT", () ->
                    assertDoesNotThrow(() -> dataScopeGuard.checkByProject(pid, "E0002"),
                            "DEPT 档同部门放行"));

            // system 动作数据：放行（备案）
            asUser(4L, "E0004", 15L, "PROJECT", () ->
                    assertDoesNotThrow(() -> dataScopeGuard.checkByProject(pid, "system"),
                            "system 动作数据放行（备案）"));
        } finally {
            memberMapper.delete(new LambdaQueryWrapper<PrjMember>().eq(PrjMember::getProjectId, pid));
        }
    }

    /** 进程内直测 Guard：临时写入登录上下文，断言后必清（防泄漏影响后续用例） */
    private void asUser(Long userId, String userCode, Long deptId, String dataScope, Runnable assertions) {
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUserCode(userCode);
        user.setDeptId(deptId);
        user.setDataScope(dataScope);
        UserContextHolder.set(user);
        try {
            assertions.run();
        } finally {
            UserContextHolder.clear();
        }
    }
}
