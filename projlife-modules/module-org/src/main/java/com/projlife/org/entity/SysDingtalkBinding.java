package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 钉钉用户绑定（SPRINT-INT2，V22）：通讯录同步产物。
 * dingtalk_userid 唯一；sys_user_id 匹配来源见 match_type（MANUAL/MOBILE/NAME/NONE，A-44）。
 */
@TableName("sys_dingtalk_binding")
public class SysDingtalkBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dingtalkUserid;

    private String unionId;

    private String name;

    /** 部门 id CSV */
    private String deptIds;

    private Long sysUserId;

    private String matchType;

    private LocalDateTime syncedAt;

    private Integer deleted;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    private Integer version;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getDingtalkUserid() { return dingtalkUserid; }

    public void setDingtalkUserid(String dingtalkUserid) { this.dingtalkUserid = dingtalkUserid; }

    public String getUnionId() { return unionId; }

    public void setUnionId(String unionId) { this.unionId = unionId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getDeptIds() { return deptIds; }

    public void setDeptIds(String deptIds) { this.deptIds = deptIds; }

    public Long getSysUserId() { return sysUserId; }

    public void setSysUserId(Long sysUserId) { this.sysUserId = sysUserId; }

    public String getMatchType() { return matchType; }

    public void setMatchType(String matchType) { this.matchType = matchType; }

    public LocalDateTime getSyncedAt() { return syncedAt; }

    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public Integer getDeleted() { return deleted; }

    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public String getCreatedBy() { return createdBy; }

    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getUpdatedBy() { return updatedBy; }

    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getVersion() { return version; }

    public void setVersion(Integer version) { this.version = version; }
}
