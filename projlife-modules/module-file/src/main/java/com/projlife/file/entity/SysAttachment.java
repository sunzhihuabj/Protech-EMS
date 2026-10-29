package com.projlife.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

/**
 * T-SYS 附件（02 册 §4.20）。BR-07：对外可下载的只有带水印版本，is_watermarked 必须真实。
 */
@TableName("sys_attachment")
public class SysAttachment extends BaseEntity {

    private String bizType;

    private String bizId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileExt;

    private String mimeType;

    private String sha256;

    private Integer version;

    private Integer isWatermarked;

    /** W1 页面水印 / W2 文件水印 / W3 照片水印 / W4 水印日志（03 册 §4.1） */
    private String watermarkType;

    private Long uploaderId;

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileExt() {
        return fileExt;
    }

    public void setFileExt(String fileExt) {
        this.fileExt = fileExt;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Integer getIsWatermarked() {
        return isWatermarked;
    }

    public void setIsWatermarked(Integer isWatermarked) {
        this.isWatermarked = isWatermarked;
    }

    public String getWatermarkType() {
        return watermarkType;
    }

    public void setWatermarkType(String watermarkType) {
        this.watermarkType = watermarkType;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }
}
