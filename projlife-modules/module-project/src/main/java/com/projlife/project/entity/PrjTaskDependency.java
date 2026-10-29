package com.projlife.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PRJ prj_task_dependency（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("prj_task_dependency")
public class PrjTaskDependency extends BaseEntity {

    /** 后置任务 */
    private Long taskId;

    /** 前置任务 */
    private Long preTaskId;

    /** FS/SS/FF/SF */
    private String depType;

    /** 滞后天数 */
    private Integer lagDays;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getPreTaskId() {
        return preTaskId;
    }

    public void setPreTaskId(Long preTaskId) {
        this.preTaskId = preTaskId;
    }

    public String getDepType() {
        return depType;
    }

    public void setDepType(String depType) {
        this.depType = depType;
    }

    public Integer getLagDays() {
        return lagDays;
    }

    public void setLagDays(Integer lagDays) {
        this.lagDays = lagDays;
    }

}