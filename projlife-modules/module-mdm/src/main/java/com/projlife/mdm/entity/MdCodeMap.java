package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_code_map（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_code_map")
public class MdCodeMap extends BaseEntity {

    /** 旧编码 */
    private String oldCode;

    /** 新编码 */
    private String newCode;

    /** MERGE/REPLACE/ALIAS */
    private String mapType;

    /** 来源 */
    private String source;

    /** 备注 */
    private String remark;

    public String getOldCode() {
        return oldCode;
    }

    public void setOldCode(String oldCode) {
        this.oldCode = oldCode;
    }

    public String getNewCode() {
        return newCode;
    }

    public void setNewCode(String newCode) {
        this.newCode = newCode;
    }

    public String getMapType() {
        return mapType;
    }

    public void setMapType(String mapType) {
        this.mapType = mapType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}