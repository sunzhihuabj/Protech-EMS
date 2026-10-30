package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_worker（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_worker")
public class EngWorker extends BaseEntity {

    /** 人员编码 */
    private String workerCode;

    /** 班组 */
    private Long crewId;

    /** 姓名 */
    private String name;

    /** 身份证 AES 密文（FR-ENG-01） */
    private String idCardCipher;

    /** 手机号 */
    private String phone;

    /** 证件 */
    private String certificatesJson;

    /** 保险到期（过期拒绝授权 UC-ENG-10） */
    private LocalDate insuranceExpire;

    /** 培训记录 */
    private String trainingRecordsJson;

    /** PENDING/ACTIVE/FROZEN/EXPIRED */
    private String status;

    public String getWorkerCode() {
        return workerCode;
    }

    public void setWorkerCode(String workerCode) {
        this.workerCode = workerCode;
    }

    public Long getCrewId() {
        return crewId;
    }

    public void setCrewId(Long crewId) {
        this.crewId = crewId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdCardCipher() {
        return idCardCipher;
    }

    public void setIdCardCipher(String idCardCipher) {
        this.idCardCipher = idCardCipher;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCertificatesJson() {
        return certificatesJson;
    }

    public void setCertificatesJson(String certificatesJson) {
        this.certificatesJson = certificatesJson;
    }

    public LocalDate getInsuranceExpire() {
        return insuranceExpire;
    }

    public void setInsuranceExpire(LocalDate insuranceExpire) {
        this.insuranceExpire = insuranceExpire;
    }

    public String getTrainingRecordsJson() {
        return trainingRecordsJson;
    }

    public void setTrainingRecordsJson(String trainingRecordsJson) {
        this.trainingRecordsJson = trainingRecordsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}