package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_quality_check（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_quality_check")
public class EngQualityCheck extends BaseEntity {

    /** 机组 */
    private Long unitId;

    /** 派工单 */
    private Long dispatchId;

    /** 检查类型 */
    private String checkType;

    /** 检查项 */
    private String checkItem;

    /** 结果 */
    private String result;

    /** 缺陷 */
    private String defectDesc;

    /** AI-102/103/104 */
    private String aiResultJson;

    /** 检查人 */
    private Long checkerId;

    /** 检查时间 */
    private LocalDateTime checkTime;

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Long getDispatchId() {
        return dispatchId;
    }

    public void setDispatchId(Long dispatchId) {
        this.dispatchId = dispatchId;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public String getCheckItem() {
        return checkItem;
    }

    public void setCheckItem(String checkItem) {
        this.checkItem = checkItem;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getDefectDesc() {
        return defectDesc;
    }

    public void setDefectDesc(String defectDesc) {
        this.defectDesc = defectDesc;
    }

    public String getAiResultJson() {
        return aiResultJson;
    }

    public void setAiResultJson(String aiResultJson) {
        this.aiResultJson = aiResultJson;
    }

    public Long getCheckerId() {
        return checkerId;
    }

    public void setCheckerId(Long checkerId) {
        this.checkerId = checkerId;
    }

    public LocalDateTime getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(LocalDateTime checkTime) {
        this.checkTime = checkTime;
    }

}