package com.projlife.org.service.code;

/**
 * 编码 18 类工厂（02 册 §五，唯一事实源）。
 * 统一规则：大写、无空格、无中文；流水 4 位（注明 3 位除外）；不可修改、允许空档。
 * 各业务模块取号只能经由本类方法 → {@link CodeSequenceService}，禁止拼字符串。
 */
public final class CodeTypes {

    private CodeTypes() {
    }

    /** 1. 合同号：{主体码}-{YYYY}-{性质码}-{业务码}-{4位}，作用域 年×性质×业务码 */
    public static CodeRequest contract(String entityCode, String year, String nature, String business) {
        return new CodeRequest("CT:" + entityCode + ":" + nature + ":" + business + ":" + year,
                entityCode + "-" + year + "-" + nature + "-" + business + "-", 4);
    }

    /** 2. 补充协议：{合同号}-BC{n}，作用域 单合同内（scope 尾段 SEQ 为固定周期标识，合同号本身作中段） */
    public static CodeRequest contractChange(String contractNo) {
        return new CodeRequest("CTBC:" + contractNo + ":SEQ", contractNo + "-BC", 1);
    }

    /** 3. 存量合同号（历史兼容，仅迁移使用）：CON-{YYYYMM}-{4位} */
    public static CodeRequest legacyContract(String yearMonth) {
        return new CodeRequest("CONL:" + yearMonth, "CON-" + yearMonth + "-", 4);
    }

    /** 4. 项目档案号：PJ{YYYY}-{4位}，全局按年 */
    public static CodeRequest projectArchive(String year) {
        return new CodeRequest("PJ:" + year, "PJ" + year + "-", 4);
    }

    /** 5. 项目执行号：PRJ-{YYYY}-{4位}，全局按年 */
    public static CodeRequest projectExec(String year) {
        return new CodeRequest("PRJ:" + year, "PRJ-" + year + "-", 4);
    }

    /** 6. 风场号：WF{YYYY}-{3位}，全局按年 */
    public static CodeRequest windFarm(String year) {
        return new CodeRequest("WF:" + year, "WF" + year + "-", 3);
    }

    /** 7. 客户主体：C{4位}，全局连续 */
    public static CodeRequest customer() {
        return new CodeRequest("CUST:GLOBAL", "C", 4);
    }

    /** 8. 供应商：S{4位}，全局连续 */
    public static CodeRequest supplier() {
        return new CodeRequest("SUP:GLOBAL", "S", 4);
    }

    /** 9. 物料：沿用现有 8 位数字主干（字母前缀冻结新增），申请时取号 */
    public static CodeRequest material() {
        return new CodeRequest("MAT:GLOBAL", "", 8);
    }

    /** 10. 虚拟件：V-{8位}，全局连续 */
    public static CodeRequest virtualPart() {
        return new CodeRequest("MATV:GLOBAL", "V-", 8);
    }

    /** 12. 档号：{主体码}-{YYYY}-{门类}-{期限}-{4位}，作用域 年×门类×期限 */
    public static CodeRequest archive(String entityCode, String year, String category, String retention) {
        return new CodeRequest("ARC:" + entityCode + ":" + category + ":" + retention + ":" + year,
                entityCode + "-" + year + "-" + category + "-" + retention + "-", 4);
    }

    /** 13. 商机号：OPP-{YYYYMM}-{4位}，按月 */
    public static CodeRequest opportunity(String yearMonth) {
        return new CodeRequest("OPP:" + yearMonth, "OPP-" + yearMonth + "-", 4);
    }

    /** 14. 采购需求：PR-{YYYYMM}-{4位} */
    public static CodeRequest purchaseRequirement(String yearMonth) {
        return new CodeRequest("PR:" + yearMonth, "PR-" + yearMonth + "-", 4);
    }

    /** 15. 采购订单：PO-{YYYYMM}-{4位} */
    public static CodeRequest purchaseOrder(String yearMonth) {
        return new CodeRequest("PO:" + yearMonth, "PO-" + yearMonth + "-", 4);
    }

    /** 16a. 生产订单：MO-{YYYYMM}-{4位} */
    public static CodeRequest productionOrder(String yearMonth) {
        return new CodeRequest("MO:" + yearMonth, "MO-" + yearMonth + "-", 4);
    }

    /** 16b. 工序单：WS-{MO后6位}-{2位}，单内序号 */
    public static CodeRequest processOrder(String moNo) {
        String tail = moNo.length() >= 6 ? moNo.substring(moNo.length() - 6) : moNo;
        return new CodeRequest("WS:" + moNo, "WS-" + tail + "-", 2);
    }

    /** 17a. 派工单：AS-{YYYYMMDD}-{4位}，按日 */
    public static CodeRequest dispatch(String date) {
        return new CodeRequest("AS:" + date, "AS-" + date + "-", 4);
    }

    /** 17b. 售后工单：WO-{YYYYMMDD}-{3位} */
    public static CodeRequest afterSales(String date) {
        return new CodeRequest("WO:" + date, "WO-" + date + "-", 3);
    }

    /** 17c. 发货单：SH-{YYYYMMDD}-{3位} */
    public static CodeRequest shipment(String date) {
        return new CodeRequest("SH:" + date, "SH-" + date + "-", 3);
    }

    /** 18a. ECN：ECN-{YYYY}-{4位} */
    public static CodeRequest ecn(String year) {
        return new CodeRequest("ECN:" + year, "ECN-" + year + "-", 4);
    }

    /** 18b. 盘点单：CT-{YYYYMMDD}-{3位} */
    public static CodeRequest count(String date) {
        return new CodeRequest("CTN:" + date, "CT-" + date + "-", 3);
    }

    /** 18c. 差异单：DF-{YYYYMM}-{4位} */
    public static CodeRequest diff(String yearMonth) {
        return new CodeRequest("DF:" + yearMonth, "DF-" + yearMonth + "-", 4);
    }

    /** 18d. 处置单：DP-{YYYYMM}-{4位} */
    public static CodeRequest disposal(String yearMonth) {
        return new CodeRequest("DP:" + yearMonth, "DP-" + yearMonth + "-", 4);
    }

    /** 18e. 补货单：RP-{YYYYMMDD}-{3位} */
    public static CodeRequest replenish(String date) {
        return new CodeRequest("RP:" + date, "RP-" + date + "-", 3);
    }

    /** 19. 报价单：QT-{YYYYMM}-{4位}（V4-01 §5.3，FR-CRM-17） */
    public static CodeRequest quotation(String yearMonth) {
        return new CodeRequest("QT:" + yearMonth, "QT-" + yearMonth + "-", 4);
    }

    /** 20. 标讯公告：BID-{YYYYMM}-{4位}（M18） */
    public static CodeRequest bidNotice(String yearMonth) {
        return new CodeRequest("BID:" + yearMonth, "BID-" + yearMonth + "-", 4);
    }

    /** 21. 数据源：SRC-{A|B|C|D}-{3位}（M18） */
    public static CodeRequest bidSource(String category) {
        return new CodeRequest("SRC:" + category, "SRC-" + category + "-", 3);
    }

    /** 22. Agent 任务：T-AGT-{YYYYMM}-{4位}（M19） */
    public static CodeRequest agentTask(String yearMonth) {
        return new CodeRequest("TAGT:" + yearMonth, "T-AGT-" + yearMonth + "-", 4);
    }

    /** 23. 看板卡片：CARD-{YYYYMMDD}-{3位}（M12） */
    public static CodeRequest kbCard(String date) {
        return new CodeRequest("CARD:" + date, "CARD-" + date + "-", 3);
    }

    /** AI 调用流水：AI-{YYYYMMDD}-{6位}（T-AI-01 call_no） */
    public static CodeRequest aiCall(String date) {
        return new CodeRequest("AIC:" + date, "AI-" + date + "-", 6);
    }

    /** 编码请求载体：scope_key + 展示前缀 + 流水位数 */
    public static class CodeRequest {

        private final String scopeKey;
        private final String displayPrefix;
        private final int digits;

        public CodeRequest(String scopeKey, String displayPrefix, int digits) {
            this.scopeKey = scopeKey;
            this.displayPrefix = displayPrefix;
            this.digits = digits;
        }

        public String getScopeKey() {
            return scopeKey;
        }

        public String getDisplayPrefix() {
            return displayPrefix;
        }

        public int getDigits() {
            return digits;
        }
    }
}
