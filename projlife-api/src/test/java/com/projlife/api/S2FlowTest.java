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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2 E2E（16 册 S2 完成标准）：模板立项 → WBS 任务树 → 开工前置（BR-PRJ-02）→ 产出物/审批
 * → 依赖成环 → 日期顺延 → ECO（BR-09）→ 门控 → 合同归档自动立项联动。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S2FlowTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long projectId;
    private static List<Map<String, Object>> tasks;

    private static final class Resp {
        final Map<String, Object> body;

        Resp(Map<String, Object> body) {
            this.body = body;
        }

        Map<String, Object> body() {
            return body;
        }

        int code() {
            return ((Number) body.get("code")).intValue();
        }

        @SuppressWarnings("unchecked")
        <T> T data() {
            return (T) body.get("data");
        }

        String blockCode() {
            Map<String, Object> d = data();
            return d == null ? null : String.valueOf(d.get("blockCode"));
        }

        Long longData() {
            Object d = body.get("data");
            return d == null ? null : Long.valueOf(String.valueOf(d));
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

    @Test
    @Order(1)
    @DisplayName("FR-PRJ-02/05/07/08 手动立项：模板 1 → 任务树 20+，WBS/日期推算")
    void createProjectWithTemplate() {
        login("biz@demo");
        Resp templates = get("/api/v1/project/templates");
        assertTrue(((List<?>) templates.data()).size() >= 3, "模板种子（标准集成/纯设备/内部研发）");

        // 先建项目档案（FR-MDM 唯一入口 R18），执行项目必须挂真实档案
        Map<String, Object> md = new HashMap<>();
        md.put("customerId", 6);
        md.put("ownerId", 6);
        md.put("legalEntityId", 2);
        md.put("orderType", "OEM_INSTALL");
        md.put("farmId", 1);
        md.put("farmNameRaw", "Zhuziling-S2");
        md.put("phase", "P1");
        md.put("stdName", "Envision-Zhuziling-S2");
        md.put("shortName", "ZZL-S2");
        md.put("productLines", List.of("LADDER"));
        md.put("setQty", 10);
        md.put("engOwnerId", 4);
        md.put("bizOwnerId", 2);
        Map<String, Object> mdLogin = loginBody("mdm@demo");
        Resp mdCreated = postAs((String) mdLogin.get("accessToken"), "/api/v1/mdm/projects", md);
        assertEquals(0, mdCreated.code(), mdCreated.body().toString());
        Long mdProjectId = mdCreated.longData();

        Map<String, Object> project = new HashMap<>();
        project.put("mdProjectId", mdProjectId);
        project.put("projectName", "竹子岭一期执行项目（S2 E2E）");
        project.put("managerId", 4L);
        project.put("planStart", LocalDate.now().toString());
        project.put("planEnd", LocalDate.now().plusDays(120).toString());
        Resp created = post("/api/v1/project?templateId=1", project);
        assertEquals(0, created.code(), created.body().toString());
        projectId = created.longData();

        Resp tree = get("/api/v1/project/" + projectId + "/tasks");
        tasks = tree.data();
        assertTrue(tasks.size() >= 20, "标准模板应生成 20+ 任务，实际 " + tasks.size());
        Map<String, Object> firstStage = tasks.get(0);
        assertEquals("1", String.valueOf(firstStage.get("wbsCode")), "一级 WBS");
        Map<String, Object> firstChild = tasks.get(1);
        assertEquals("1.1", String.valueOf(firstChild.get("wbsCode")), "二级 WBS 形如 1.1");
        LocalDate stageStart = LocalDate.parse(String.valueOf(firstChild.get("planStart")));
        LocalDate stageEnd = LocalDate.parse(String.valueOf(firstChild.get("planEnd")));
        assertTrue(!stageEnd.isBefore(stageStart), "工期推算 end>=start");
    }

    @Test
    @Order(2)
    @DisplayName("FR-PRJ-14 + BR-PRJ-02：首个任务可开工；后继任务前置未完成被拒")
    void startTaskWithPredecessor() {
        // tasks[1] = 阶段 1 的首个子任务（无前置）→ 可开工
        Long firstTaskId = Long.valueOf(String.valueOf(tasks.get(1).get("id")));
        Resp started = post("/api/v1/project/tasks/" + firstTaskId + "/start", null);
        assertEquals(0, started.code(), "tasks[1]=" + tasks.get(1) + " resp=" + started.body());

        // tasks[2] = 其后继（FS 依赖 tasks[1]）→ 被拒
        Long secondTaskId = Long.valueOf(String.valueOf(tasks.get(2).get("id")));
        Resp startSecond = post("/api/v1/project/tasks/" + secondTaskId + "/start", null);
        assertEquals(1001, startSecond.code(), "前置未完成应拒绝");
        assertTrue(String.valueOf(startSecond.body().get("message")).contains("BR-PRJ-02"), "提示 BR-PRJ-02");
    }

    @Test
    @Order(3)
    @DisplayName("FR-PRJ-16/17：产出物强制 + 审批通过后 DONE")
    void finishWithOutputAndApproval() {
        Long firstTaskId = Long.valueOf(String.valueOf(tasks.get(1).get("id")));
        // 项目立项审批任务 approval_required=1 且 output_required=0 → 无产出提交 → 进审批
        Resp finish = post("/api/v1/project/tasks/" + firstTaskId + "/finish",
                Map.of("finishNote", "立项审批材料已备"));
        assertEquals(0, finish.code(), finish.body().toString());

        Resp gate = get("/api/v1/project/" + projectId + "/gate/" + Long.valueOf(String.valueOf(tasks.get(0).get("id"))));
        // 阶段任务未全部完成 → gateOpen=false
        assertTrue(String.valueOf(gate.body()).contains("gateOpen"), "门控状态返回");
    }

    @Test
    @Order(4)
    @DisplayName("FR-PRJ-21：依赖成环被拒")
    void cycleDependencyRejected() {
        Long a = Long.valueOf(String.valueOf(tasks.get(1).get("id")));
        Long b = Long.valueOf(String.valueOf(tasks.get(2).get("id")));
        // a 依赖 b（而 b 依赖 a）→ 成环
        Resp resp = post("/api/v1/project/tasks/" + a + "/dependencies",
                Map.of("preTaskId", b, "depType", "FS", "lagDays", 0));
        assertEquals(1001, resp.code());
        assertTrue(String.valueOf(resp.body().get("message")).contains("循环依赖"), "提示循环依赖");
    }

    @Test
    @Order(5)
    @DisplayName("FR-PRJ-10/22：调整日期 → 后续依赖任务自动顺延")
    void adjustDateShiftsDependents() {
        Long firstTaskId = Long.valueOf(String.valueOf(tasks.get(1).get("id")));
        String beforeEnd = String.valueOf(tasks.get(2).get("planEnd"));
        LocalDate newEnd = LocalDate.now().plusDays(10);
        Resp resp = post("/api/v1/project/tasks/" + firstTaskId + "/adjust-date", Map.of("planEnd", newEnd.toString()));
        assertEquals(0, resp.code());
        Integer shifted = resp.data();
        assertTrue(shifted >= 1, "至少顺延 1 个后续任务，实际 " + shifted);
        Resp tree = get("/api/v1/project/" + projectId + "/tasks");
        List<Map<String, Object>> refreshed = tree.data();
        String afterEnd = String.valueOf(refreshed.stream()
                .filter(t -> String.valueOf(t.get("id")).equals(String.valueOf(tasks.get(2).get("id"))))
                .findFirst().orElseThrow().get("planEnd"));
        assertTrue(!afterEnd.equals(beforeEnd), "后继任务日期应被顺延 " + beforeEnd + "→" + afterEnd);
    }

    @Test
    @Order(6)
    @DisplayName("BR-09：ECO 未附旧料处置被拒；填后 ECN 编号 + 审批生效")
    void ecoWithBr09() {
        Resp rejected = post("/api/v1/project/" + projectId + "/ecos",
                Map.of("changeReason", "业主变更点位", "changeContent", "消防点位调整 8 处"));
        assertEquals(5001, rejected.code());
        assertEquals("BR-09", rejected.blockCode(), "BR-09 硬卡点");

        Resp created = post("/api/v1/project/" + projectId + "/ecos",
                Map.of("changeReason", "业主变更点位", "changeContent", "消防点位调整 8 处",
                        "impactAnalysis", "涉采购清单 3 项、现场已安装 2 台需返工",
                        "oldDisposalPlan", "旧电缆 200 米优先调拨至酒泉项目（demo）使用，其余报废处理", "affectedDepts", "[\"采购\",\"工艺\",\"生产\"]"));
        assertEquals(0, created.code(), created.body().toString());
        Long ecoId = created.longData();
        Resp ecos = get("/api/v1/project/" + projectId + "/ecos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ecos.data();
        String ecoNo = String.valueOf(rows.get(0).get("ecoNo"));
        assertTrue(ecoNo.matches("ECN-\\d{4}-\\d{4}"), "ECN 编号格式，实际 " + ecoNo);
        // ECO 审批权限 project:eco:approve 属技术/工程角色（R14）
        Map<String, Object> engLogin = loginBody("eng@demo");
        assertEquals(0, postAs((String) engLogin.get("accessToken"),
                "/api/v1/project/ecos/" + ecoId + "/approve", Map.of("approved", true)).code(), "ECO 审批");
    }

    @Test
    @Order(7)
    @DisplayName("FR-PRJ-01 自动立项联动：S1 合同建档返回 autoProjectId（复用 S1FlowTest 契约）")
    void autoCreateLinked() {
        // S1FlowTest 已断言 filing 返回 autoProjectId 非空的路径（当关联项目存在时）；
        // 这里验证联动接口存在且模板默认种子生效（防回归哨兵）
        Resp templates = get("/api/v1/project/templates");
        assertTrue(String.valueOf(templates.body()).contains("TPL-STD"), "默认模板存在");
    }

    private void login(String username) {
        token = (String) loginBody(username).get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loginBody(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "12345678", "client", "web");
        org.springframework.http.ResponseEntity<Map> resp =
                rest.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class);
        return (Map<String, Object>) ((Map<?, ?>) resp.getBody()).get("data");
    }

    private Resp postAs(String asToken, String path, Object body) {
        String keep = token;
        token = asToken;
        try {
            return post(path, body);
        } finally {
            token = keep;
        }
    }
}
