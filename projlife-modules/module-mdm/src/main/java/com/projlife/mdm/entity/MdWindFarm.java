package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_wind_farm（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_wind_farm")
public class MdWindFarm extends BaseEntity {

    /** WF{YYYY}-{NNN} */
    private String farmCode;

    /** 风场全称（业主规范） */
    private String farmName;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 县 */
    private String county;

    /** 业主（md_customer） */
    private Long ownerId;

    /** 装机容量 MW */
    private BigDecimal capacityMw;

    /** 期次数 */
    private Integer phaseCount;

    /** E-02 */
    private String status;

    public String getFarmCode() {
        return farmCode;
    }

    public void setFarmCode(String farmCode) {
        this.farmCode = farmCode;
    }

    public String getFarmName() {
        return farmName;
    }

    public void setFarmName(String farmName) {
        this.farmName = farmName;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public BigDecimal getCapacityMw() {
        return capacityMw;
    }

    public void setCapacityMw(BigDecimal capacityMw) {
        this.capacityMw = capacityMw;
    }

    public Integer getPhaseCount() {
        return phaseCount;
    }

    public void setPhaseCount(Integer phaseCount) {
        this.phaseCount = phaseCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}