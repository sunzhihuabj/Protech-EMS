package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-KB kb_meeting（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("kb_meeting")
public class KbMeeting extends BaseEntity {

    /** 看板 */
    private Long boardId;

    /** 会议日期 */
    private LocalDate meetingDate;

    /** 时长（30 分钟议程） */
    private Integer durationMinutes;

    /** 参会人 */
    private String attendees;

    /** 红灯数 */
    private Integer redCount;

    /** 过堂数 */
    private Integer reviewedCount;

    /** 闭环数 */
    private Integer closedCount;

    /** 今日承诺 */
    private String promisesJson;

    /** 纪要 */
    private String minutes;

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public LocalDate getMeetingDate() {
        return meetingDate;
    }

    public void setMeetingDate(LocalDate meetingDate) {
        this.meetingDate = meetingDate;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getAttendees() {
        return attendees;
    }

    public void setAttendees(String attendees) {
        this.attendees = attendees;
    }

    public Integer getRedCount() {
        return redCount;
    }

    public void setRedCount(Integer redCount) {
        this.redCount = redCount;
    }

    public Integer getReviewedCount() {
        return reviewedCount;
    }

    public void setReviewedCount(Integer reviewedCount) {
        this.reviewedCount = reviewedCount;
    }

    public Integer getClosedCount() {
        return closedCount;
    }

    public void setClosedCount(Integer closedCount) {
        this.closedCount = closedCount;
    }

    public String getPromisesJson() {
        return promisesJson;
    }

    public void setPromisesJson(String promisesJson) {
        this.promisesJson = promisesJson;
    }

    public String getMinutes() {
        return minutes;
    }

    public void setMinutes(String minutes) {
        this.minutes = minutes;
    }

}