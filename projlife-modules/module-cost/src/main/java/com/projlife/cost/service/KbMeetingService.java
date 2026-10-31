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
}
