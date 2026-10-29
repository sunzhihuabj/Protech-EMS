package com.projlife.common.api;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页出参。约定：records 永不为 null（16 册 §4.4 空值约定）。
 */
public class PageResp<T> {

    private long total;
    private long pages;
    private List<T> records;

    public PageResp() {
        this.records = Collections.emptyList();
    }

    public PageResp(long total, long pages, List<T> records) {
        this.total = total;
        this.pages = pages;
        this.records = records == null ? Collections.emptyList() : records;
    }

    public static <T> PageResp<T> of(long total, List<T> records) {
        return new PageResp<>(total, 0L, records);
    }

    public static <T> PageResp<T> empty() {
        return new PageResp<>(0, 0, Collections.emptyList());
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records == null ? Collections.emptyList() : records;
    }
}
