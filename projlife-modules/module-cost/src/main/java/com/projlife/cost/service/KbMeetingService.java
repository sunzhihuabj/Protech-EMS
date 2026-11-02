package com.projlife.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.cost.entity.KbBoard;
import com.projlife.cost.entity.KbMeeting;
import com.projlife.cost.mapper.KbBoardMapper;
import com.projlife.cost.mapper.KbMeetingMapper;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议纪要服务（FR-KB-11，SPRINT-KB1 增强）：会议模式结束 → 纪要落库 kb_meeting，只读可导出。
 * 逐卡三问结论由前端组装为 promises_json（[{title,where,who,when,result:CLOSED|SKIPPED}]），
 * 后端校验合法 JSON（A-30：MySQL JSON 列拒收非法串）并在 detail/PDF 中还原逐卡结论。
 */
@Service
public class KbMeetingService {

    private final KbMeetingMapper meetingMapper;
    private final KbBoardMapper boardMapper;
    private final ObjectMapper objectMapper;

    public KbMeetingService(KbMeetingMapper meetingMapper, KbBoardMapper boardMapper, ObjectMapper objectMapper) {
        this.meetingMapper = meetingMapper;
        this.boardMapper = boardMapper;
        this.objectMapper = objectMapper;
    }

    /** 纪要落库（FR-KB-11）：board_id + 四段计数 + promises_json（逐卡三问，合法 JSON 校验） */
    @RequirePermission("kb:card:manage")
    @Transactional
    public KbMeeting save(KbMeeting meeting) {
        if (meeting.getBoardId() == null) {
            meeting.setBoardId(1L);
        }
        if (meeting.getMeetingDate() == null) {
            meeting.setMeetingDate(LocalDate.now());
        }
        if (meeting.getDurationMinutes() == null) {
            meeting.setDurationMinutes(30);
        }
        if (meeting.getRedCount() == null) {
            meeting.setRedCount(0);
        }
        if (meeting.getReviewedCount() == null) {
            meeting.setReviewedCount(0);
        }
        if (meeting.getClosedCount() == null) {
            meeting.setClosedCount(0);
        }
        // promises_json：MySQL JSON 列仅收合法 JSON（A-30）；空串归 null
        if (meeting.getPromisesJson() != null && meeting.getPromisesJson().trim().isEmpty()) {
            meeting.setPromisesJson(null);
        }
        if (meeting.getPromisesJson() != null) {
            try {
                JsonNode node = objectMapper.readTree(meeting.getPromisesJson());
                if (!node.isArray()) {
                    throw new IllegalArgumentException("promisesJson 必须为 JSON 数组");
                }
            } catch (Exception ex) {
                throw BizException.of(ErrorCode.PARAM_INVALID,
                        "promisesJson 非法（须为逐卡结论 JSON 数组）：" + ex.getMessage());
            }
        }
        // minutes/attendees 超长截断（VARCHAR(2000)/500），避免 500
        if (meeting.getMinutes() != null && meeting.getMinutes().length() > 2000) {
            meeting.setMinutes(meeting.getMinutes().substring(0, 2000));
        }
        if (meeting.getAttendees() != null && meeting.getAttendees().length() > 500) {
            meeting.setAttendees(meeting.getAttendees().substring(0, 500));
        }
        meetingMapper.insert(meeting);
        return meeting;
    }

    /** 纪要列表（按板） */
    public List<KbMeeting> listByBoard(Long boardId) {
        return meetingMapper.selectList(new LambdaQueryWrapper<KbMeeting>()
                .eq(KbMeeting::getBoardId, boardId)
                .eq(KbMeeting::getDeleted, 0)
                .orderByDesc(KbMeeting::getMeetingDate));
    }

    /** 看板清单（boardCode→id 解析用，SPRINT-KB1） */
    public List<KbBoard> boards() {
        return boardMapper.selectList(new LambdaQueryWrapper<KbBoard>()
                .eq(KbBoard::getDeleted, 0)
                .orderByAsc(KbBoard::getSortNo));
    }

    /** 纪要详情：meeting + minutesText + items（逐卡结论，promisesJson 解析还原） */
    public Map<String, Object> detail(Long id) {
        KbMeeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw com.projlife.common.exception.BizException.notFound("纪要不存在: " + id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("meeting", meeting);
        result.put("minutesText", meeting.getMinutes() == null ? "" : meeting.getMinutes());
        result.put("items", parseItems(meeting.getPromisesJson()));
        return result;
    }

    /** promisesJson → 逐卡结论列表（容错：非法/空返回空列表；H2 JSON 包裹解包，A-30） */
    public List<Map<String, Object>> parseItems(String promisesJson) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (promisesJson == null || promisesJson.trim().isEmpty()) {
            return items;
        }
        String text = promisesJson.trim();
        // H2 JSON 列 getString 可能再包一层引号（A-30 口径），解包一次
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 1) {
            text = text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (!node.isArray()) {
                return items;
            }
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode item : arr) {
                Map<String, Object> row = new HashMap<>();
                row.put("title", item.path("title").asText(""));
                row.put("where", item.path("where").asText(""));
                row.put("who", item.path("who").asText(""));
                row.put("when", item.path("when").asText(""));
                row.put("result", item.path("result").asText("SKIPPED"));
                items.add(row);
            }
        } catch (Exception ignored) {
            // 容错还原：历史脏数据不阻塞纪要页
        }
        return items;
    }

    /** 纪要 PDF 导出（FR-KB-11，PDFBox 亮色白底，验收 11）：概要 + 逐卡结论。SPRINT-KB1 修复实体强转与中文渲染。 */
    public byte[] meetingPdf(Long id) {
        Map<String, Object> detail = detail(id);
        com.projlife.cost.entity.KbMeeting m = (com.projlife.cost.entity.KbMeeting) detail.get("meeting");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                    org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(page);
            // CJK 字体：内置 HELVETICA 编码不了中文（存量缺陷，E2E 首曝）；按平台常见字体加载，无则 ASCII 降级
            org.apache.pdfbox.pdmodel.font.PDFont cjk = loadCjkFont(doc);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page,
                         org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
                float y = 800;
                text(cs, cjk, 16, 50, y, "Meeting Minutes 会议纪要");
                y -= 28;
                String[][] rows = {
                    {"Board ID", String.valueOf(m.getBoardId())},
                    {"Date", String.valueOf(m.getMeetingDate())},
                    {"Duration(min)", String.valueOf(m.getDurationMinutes())},
                    {"Attendees", String.valueOf(m.getAttendees())},
                    {"Red Cards", String.valueOf(m.getRedCount())},
                    {"Reviewed", String.valueOf(m.getReviewedCount())},
                    {"Closed", String.valueOf(m.getClosedCount())},
                    {"Minutes", String.valueOf(detail.get("minutesText"))}
                };
                for (String[] row : rows) {
                    text(cs, cjk, 11, 50, y, row[0] + ": " + row[1]);
                    y -= 20;
                }
                y -= 10;
                text(cs, cjk, 12, 50, y, "Card Review Log (" + items.size() + ")");
                y -= 18;
                int i = 1;
                for (Map<String, Object> item : items) {
                    if (y < 60) {
                        break; // 单页护栏
                    }
                    String line = "#" + i++ + " [" + item.get("result") + "] " + item.get("title")
                            + " 卡在哪：" + item.get("where") + " 谁解决：" + item.get("who")
                            + " 何时：" + item.get("when");
                    text(cs, cjk, 10, 50, y, line);
                    y -= 16;
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("meeting PDF failed", e);
        }
    }

    /** 按平台常见路径加载 CJK 字体；失败返回 null（调用方 ASCII 降级） */
    private org.apache.pdfbox.pdmodel.font.PDType0Font loadCjkFont(org.apache.pdfbox.pdmodel.PDDocument doc) {
        String[] candidates = {
            "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/msyh.ttf", "C:/Windows/Fonts/simsun.ttf",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc"
        };
        for (String path : candidates) {
            try {
                java.io.File f = new java.io.File(path);
                if (f.exists()) {
                    return org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc, f);
                }
            } catch (Exception ignored) {
                // 尝试下一个候选字体
            }
        }
        return null;
    }

    /** 文本绘制：CJK 字体可用时原样渲染；否则非 ASCII 降级为 '?'（不抛异常） */
    private void text(org.apache.pdfbox.pdmodel.PDPageContentStream cs,
                      org.apache.pdfbox.pdmodel.font.PDFont cjk,
                      float size, float x, float y, String content) throws java.io.IOException {
        String s = content == null ? "" : content;
        org.apache.pdfbox.pdmodel.font.PDFont font;
        if (cjk != null) {
            font = cjk;
        } else {
            font = org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA;
            s = s.replaceAll("[^\\x20-\\x7E]", "?");
        }
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
