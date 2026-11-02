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
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPRINT-KB1 E2E（FR-KB-11 增强）：纪要落库 promises_json 逐卡结论（合法 JSON 校验 1001）
 * → 详情还原 items → 看板清单 boardCode→id → PDF 导出非空。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S27KbMeetingTest {

    @Autowired
    private TestRestTemplate rest;

    private static String token;
    private static Long meetingId;

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

        Map<String, Object> body() {
            return body;
        }
    }

    private Resp post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new Resp(rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody());
    }

    private Resp get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new Resp(rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody());
    }

    private void loginAs(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("username", username, "password", "ProjLife@2026", "client", "web");
        token = (String) ((Map<?, ?>) ((Map<?, ?>) rest
                .postForEntity("/api/v1/auth/login", new HttpEntity<>(body, headers), Map.class)
                .getBody()).get("data")).get("accessToken");
    }

    @Test
    @Order(1)
    @DisplayName("纪要落库：promisesJson 逐卡结论（2 闭环 + 1 未闭环）+ 计数回显")
    void saveWithPromises() {
        loginAs("admin@demo");
        String promises = "[{\"title\":\"demo 叶片采购卡住\",\"where\":\"供应商产能\",\"who\":\"王工\",\"when\":\"2026-11-10\",\"result\":\"CLOSED\"},"
                + "{\"title\":\"demo 吊装队伍未定\",\"where\":\"分包招标\",\"who\":\"李经理\",\"when\":\"2026-11-12\",\"result\":\"CLOSED\"},"
                + "{\"title\":\"demo 并网手续滞后\",\"where\":\"电网审批\",\"who\":\"\",\"when\":\"\",\"result\":\"SKIPPED\"}]";
        Resp created = post("/api/v1/kb/meetings", Map.of(
                "boardId", 2, "meetingDate", "2026-11-03", "durationMinutes", 32,
                "redCount", 3, "reviewedCount", 3, "closedCount", 2,
                "promisesJson", promises,
                "minutes", "SPRINT-KB1 demo 纪要：过堂 3 张（闭环 2）。"));
        assertEquals(0, created.code(), created.body().toString());
        Map<?, ?> meeting = (Map<?, ?>) created.data();
        meetingId = Long.valueOf(String.valueOf(meeting.get("id")));
        assertNotNull(meetingId);
        assertEquals(2, ((Number) meeting.get("closedCount")).intValue());
    }

    @Test
    @Order(2)
    @DisplayName("纪要详情：promisesJson 还原为 items 逐卡结论")
    void detailItems() {
        Resp detail = get("/api/v1/kb/meetings/" + meetingId);
        assertEquals(0, detail.code(), detail.body().toString());
        Map<?, ?> data = (Map<?, ?>) detail.data();
        List<?> items = (List<?>) data.get("items");
        assertEquals(3, items.size(), "逐卡结论应还原 3 条");
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertEquals("demo 叶片采购卡住", first.get("title"));
        assertEquals("CLOSED", first.get("result"));
        assertEquals("王工", first.get("who"));
        Map<?, ?> third = (Map<?, ?>) items.get(2);
        assertEquals("SKIPPED", third.get("result"));
        assertEquals("SPRINT-KB1 demo 纪要：过堂 3 张（闭环 2）。", data.get("minutesText"));
    }

    @Test
    @Order(3)
    @DisplayName("promisesJson 非法 JSON → 1001 拦截（A-30：MySQL JSON 列拒收非法串）")
    void invalidPromisesRejected() {
        Resp bad = post("/api/v1/kb/meetings", Map.of(
                "boardId", 2, "promisesJson", "这不是JSON",
                "redCount", 1, "reviewedCount", 1, "closedCount", 0));
        assertEquals(1001, bad.code(), bad.body().toString());
    }

    @Test
    @Order(4)
    @DisplayName("看板清单：boardCode→id 解析数据源（KB01/KB02 种子）")
    void boardsList() {
        Resp boards = get("/api/v1/kb/boards");
        assertEquals(0, boards.code(), boards.body().toString());
        List<?> list = boards.data();
        assertTrue(list != null && !list.isEmpty(), "应有种子看板");
        Map<?, ?> first = (Map<?, ?>) list.get(0);
        assertNotNull(first.get("boardCode"));
        assertNotNull(first.get("id"));
    }

    @Test
    @Order(4)
    @DisplayName("[单元] service 直生 PDF（CJK 字体+逐卡结论，防实体强转回归）")
    void pdfDirectProbe() {
        com.projlife.cost.mapper.KbMeetingMapper mm = Mockito.mock(com.projlife.cost.mapper.KbMeetingMapper.class);
        com.projlife.cost.mapper.KbBoardMapper bm = Mockito.mock(com.projlife.cost.mapper.KbBoardMapper.class);
        com.projlife.cost.entity.KbMeeting m = new com.projlife.cost.entity.KbMeeting();
        m.setId(9L);
        m.setPromisesJson("[{\"title\":\"中文标题\",\"result\":\"CLOSED\",\"where\":\"位置\",\"who\":\"谁\",\"when\":\"何时\"}]");
        m.setMinutes("中文纪要内容");
        Mockito.when(mm.selectById(9L)).thenReturn(m);
        com.projlife.cost.service.KbMeetingService svc =
                new com.projlife.cost.service.KbMeetingService(mm, bm, new com.fasterxml.jackson.databind.ObjectMapper());
        byte[] pdf = svc.meetingPdf(9L);
        assertTrue(pdf.length > 500);
    }

    @Test
    @Order(5)
    @DisplayName("纪要 PDF：非空导出（含逐卡结论行）")
    void pdfExport() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        byte[] pdf = rest.exchange("/api/v1/kb/meetings/" + meetingId + "/pdf",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class).getBody();
        assertNotNull(pdf);
        assertTrue(pdf.length > 500, "PDF 应有实质内容");
        // PDF 魔数
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
