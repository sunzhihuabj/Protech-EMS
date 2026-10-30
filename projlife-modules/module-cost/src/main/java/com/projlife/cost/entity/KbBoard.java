package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-KB kb_board（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("kb_board")
public class KbBoard extends BaseEntity {

    /** 板编码 UK */
    private String boardCode;

    /** 名称 */
    private String boardName;

    /** TOTAL/CHAIN/FUNC */
    private String boardLevel;

    /** 归属部门 */
    private Long deptId;

    /** 链主 */
    private Long chainMasterId;

    /** DAILY/WEEKLY */
    private String rhythm;

    /** 序号 */
    private Integer sortNo;

    /** E-02 */
    private String status;

    public String getBoardCode() {
        return boardCode;
    }

    public void setBoardCode(String boardCode) {
        this.boardCode = boardCode;
    }

    public String getBoardName() {
        return boardName;
    }

    public void setBoardName(String boardName) {
        this.boardName = boardName;
    }

    public String getBoardLevel() {
        return boardLevel;
    }

    public void setBoardLevel(String boardLevel) {
        this.boardLevel = boardLevel;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getChainMasterId() {
        return chainMasterId;
    }

    public void setChainMasterId(Long chainMasterId) {
        this.chainMasterId = chainMasterId;
    }

    public String getRhythm() {
        return rhythm;
    }

    public void setRhythm(String rhythm) {
        this.rhythm = rhythm;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}