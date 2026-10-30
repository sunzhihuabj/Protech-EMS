package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_crew（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_crew")
public class EngCrew extends BaseEntity {

    /** 班组编码 */
    private String crewCode;

    /** 班组名称 */
    private String crewName;

    /** 所属供应商 */
    private Long supplierId;

    /** 班组长 */
    private String leaderName;

    /** 联系电话 */
    private String leaderPhone;

    /** 资质等级 */
    private String qualificationLevel;

    /** 评分 */
    private BigDecimal score;

    /** E-02 */
    private String status;

    /** 黑名单 */
    private Integer blacklistFlag;

    public String getCrewCode() {
        return crewCode;
    }

    public void setCrewCode(String crewCode) {
        this.crewCode = crewCode;
    }

    public String getCrewName() {
        return crewName;
    }

    public void setCrewName(String crewName) {
        this.crewName = crewName;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
    }

    public String getLeaderPhone() {
        return leaderPhone;
    }

    public void setLeaderPhone(String leaderPhone) {
        this.leaderPhone = leaderPhone;
    }

    public String getQualificationLevel() {
        return qualificationLevel;
    }

    public void setQualificationLevel(String qualificationLevel) {
        this.qualificationLevel = qualificationLevel;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getBlacklistFlag() {
        return blacklistFlag;
    }

    public void setBlacklistFlag(Integer blacklistFlag) {
        this.blacklistFlag = blacklistFlag;
    }

}