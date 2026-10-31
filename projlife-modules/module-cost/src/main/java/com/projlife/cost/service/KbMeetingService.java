package com.projlife.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.cost.entity.KbMeeting;
import com.projlife.cost.mapper.KbMeetingMapper;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议纪要服务（FR-KB-11）：会议模式结束 → 纪要落库 kb_meeting，只读可导出。
 * 逐卡三问结论由前端组合为 minutes/promises_json。
 */
@Service
public class KbMeetingService {

    private final KbMeetingMapper meetingMapper;

    public KbMeetingService(KbMeetingMapper meetingMapper) {
        this.meetingMapper = meetingMapper;
    }

    /** 纪要落库（FR-KB-11）：board_id + 四段计数 + promises_json（逐卡三问） */
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

    /** 纪要详情 + 概要（供纪要页/导出） */
    public Map<String, Object> detail(Long id) {
        KbMeeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw com.projlife.common.exception.BizException.notFound("纪要不存在: " + id);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("meeting", meeting);
        result.put("minutesText", meeting.getMinutes() == null ? "" : meeting.getMinutes());
        return result;
    }

    /** 纪要 PDF 导出（FR-KB-11，PDFBox 亮色白底，验收 11） */
    public byte[] meetingPdf(Long id) {
        Map<String, Object> detail = detail(id);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) detail.get("meeting");
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                    org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page,
                         org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
                float y = 800;
                cs.beginText();
                cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(50, y);
                cs.showText("Meeting Minutes");
                cs.endText();
                y -= 28;
                String[][] rows = {
                    {"Board ID", String.valueOf(m.get("boardId"))},
                    {"Date", String.valueOf(m.get("meetingDate"))},
                    {"Duration(min)", String.valueOf(m.get("durationMinutes"))},
                    {"Attendees", String.valueOf(m.get("attendees"))},
                    {"Red Cards", String.valueOf(m.get("redCount"))},
                    {"Reviewed", String.valueOf(m.get("reviewedCount"))},
                    {"Closed", String.valueOf(m.get("closedCount"))},
                    {"Minutes", String.valueOf(detail.get("minutesText"))}
                };
                for (String[] row : rows) {
                    cs.beginText();
                    cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(50, y);
                    cs.showText(row[0] + ": " + row[1]);
                    cs.endText();
                    y -= 20;
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("meeting PDF failed", e);
        }
    }
}
