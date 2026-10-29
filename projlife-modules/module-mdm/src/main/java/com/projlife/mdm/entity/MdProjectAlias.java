package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_project_alias（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_project_alias")
public class MdProjectAlias extends BaseEntity {

    /** 项目档案 id */
    private Long mdProjectId;

    /** 别名 */
    private String aliasName;

    /** 历史写法/口语/错别字 */
    private String aliasType;

    /** 来源 */
    private String source;

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public String getAliasType() {
        return aliasType;
    }

    public void setAliasType(String aliasType) {
        this.aliasType = aliasType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

}