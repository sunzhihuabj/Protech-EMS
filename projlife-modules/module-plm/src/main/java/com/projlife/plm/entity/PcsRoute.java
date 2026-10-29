package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM pcs_route（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("pcs_route")
public class PcsRoute extends BaseEntity {

    /** 适用 BOM */
    private Long bomHeaderId;

    /** 路线编号 */
    private String routeNo;

    /** 路线名称 */
    private String routeName;

    /** 版本 */
    private String version;

    /** E-02 */
    private String status;

    public Long getBomHeaderId() {
        return bomHeaderId;
    }

    public void setBomHeaderId(Long bomHeaderId) {
        this.bomHeaderId = bomHeaderId;
    }

    public String getRouteNo() {
        return routeNo;
    }

    public void setRouteNo(String routeNo) {
        this.routeNo = routeNo;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}