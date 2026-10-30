package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-THD thd_delivery_note（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("thd_delivery_note")
public class ThdDeliveryNote extends BaseEntity {

    /** PO */
    private Long poId;

    /** 送货单号 */
    private String noteNo;

    /** 送货日期 */
    private LocalDate deliveryDate;

    /** 明细 */
    private String itemsJson;

    /** 打印时间 */
    private LocalDateTime printedAt;

    /** 打印人 */
    private Long printedBy;

    public Long getPoId() {
        return poId;
    }

    public void setPoId(Long poId) {
        this.poId = poId;
    }

    public String getNoteNo() {
        return noteNo;
    }

    public void setNoteNo(String noteNo) {
        this.noteNo = noteNo;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public LocalDateTime getPrintedAt() {
        return printedAt;
    }

    public void setPrintedAt(LocalDateTime printedAt) {
        this.printedAt = printedAt;
    }

    public Long getPrintedBy() {
        return printedBy;
    }

    public void setPrintedBy(Long printedBy) {
        this.printedBy = printedBy;
    }

}