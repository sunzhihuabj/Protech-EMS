package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

/**
 * T-SYS 字典类型（E-01~E-42 全部在此，is_system=1 为规格枚举，删除被拒 S0-8）。
 */
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    private String typeCode;

    private String typeName;

    /** 1=规格内置枚举，禁删禁改 item_code */
    private Integer isSystem;

    private String remark;

    private String status;

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Integer getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Integer isSystem) {
        this.isSystem = isSystem;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
