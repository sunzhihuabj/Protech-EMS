package com.projlife.agent.service;

import com.projlife.bid.entity.BidMatch;
import com.projlife.bid.service.BidService;
import com.projlife.common.api.PageReq;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.crm.api.CrmApi;
import com.projlife.agent.entity.AgtSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力执行器：将白名单能力路由到业务模块（跨模块只经 api 包/服务门面）。
 * 写能力经内存任务表演示状态机 ACCEPTED → RUNNING → SUCCEEDED（生产落 job 队列）。
 */
@Service
public class AgentCapabilityExecutor {

    /** 同步查询返回行数上限（FR-AGT-05 ≤200 行） */
    private static final int MAX_ROWS = 200;

    private final BidService bidService;
    private final CrmApi crmApi;

    /** 演示任务表：taskId -> 状态/结果（A-27：生产替换为 job 队列） */
    private final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();

    public AgentCapabilityExecutor(BidService bidService, CrmApi crmApi) {
        this.bidService = bidService;
        this.crmApi = crmApi;
    }

    public Map<String, Object> executeSync(String capability, AgtSession session, Map<String, Object> params) {
        Map<String, Object> data = new HashMap<>();
        switch (capability) {
            case "bid.notice.search":
                data.put("rows", noticeRows(params));
                data.put("maxRows", MAX_ROWS);
                break;
            case "bid.notice.detail":
                Long noticeId = requireLong(params, "noticeId");
                com.projlife.bid.entity.BidNotice notice = bidService.noticeDetail(noticeId);
                BidMatch match = bidService.matchOf(noticeId);
                data.put("notice", notice);
                data.put("match", match);
                break;
            case "crm.customer.search":
                data.put("rows", demoRows("customer", params));
                break;
            case "mdm.project.search":
                data.put("rows", demoRows("project", params));
                break;
            case "kb.card.search":
                data.put("rows", demoRows("kbCard", params));
                break;
            case "worker.my.task":
                data.put("onBehalfOf", session.getOnBehalfOf());
                data.put("rows", demoRows("workerTask", params));
                break;
            case "assistant.project.summary":
            case "assistant.cost.summary":
            case "assistant.schedule.query":
                data.put("summary", "演示摘要：能力 " + capability + " 已返回（脱敏后口径，V4-06 M19）");
                data.put("deidentified", true);
                break;
            default:
                throw BizException.blocked("AGT-FORBIDDEN", "能力未注册: " + capability, null);
        }
        return data;
    }

    public Map<String, Object> executeAsync(String capability, AgtSession session,
                                            Map<String, Object> params, String idempotencyKey, String taskId) {
        Map<String, Object> data = new HashMap<>();
        switch (capability) {
            case "crm.opportunity.create": {
                Long noticeId = requireLong(params, "noticeId");
                Long customerId = requireLong(params, "customerId");
                BigDecimal amount = params.get("expectAmount") == null
                        ? null : new BigDecimal(String.valueOf(params.get("expectAmount")));
                Long oppId = crmApi.createOpportunityFromBid(
                        String.valueOf(params.getOrDefault("oppName", "Agent 转商机")),
                        customerId, amount,
                        String.valueOf(params.getOrDefault("summary", "")), noticeId);
                data.put("opportunityId", oppId);
                break;
            }
            case "worker.report.submit":
            case "worker.attendance.backfill":
                data.put("onBehalfOf", session.getOnBehalfOf());
                break;
            default:
                throw BizException.blocked("AGT-FORBIDDEN", "能力未注册: " + capability, null);
        }
        // 幂等：同 Idempotency-Key 复用结果（taskId 由服务层统一编号）
        Map<String, Object> record = new HashMap<>();
        record.put("taskId", taskId);
        record.put("capability", capability);
        record.put("status", "SUCCEEDED");
        record.put("idempotencyKey", idempotencyKey);
        record.put("result", data);
        tasks.put(taskId, record);
        data.put("taskState", record.get("status"));
        return data;
    }

    public Map<String, Object> taskStatus(String taskId) {
        Map<String, Object> record = tasks.get(taskId);
        if (record == null) {
            throw BizException.notFound("任务不存在: " + taskId);
        }
        Map<String, Object> status = new HashMap<>();
        status.put("taskId", taskId);
        status.put("capability", record.get("capability"));
        status.put("status", record.get("status"));
        status.put("result", record.get("result"));
        return status;
    }

    private List<Map<String, Object>> noticeRows(Map<String, Object> params) {
        PageReq pageReq = new PageReq();
        pageReq.setPageNum(1);
        pageReq.setPageSize(MAX_ROWS);
        List<com.projlife.bid.entity.BidNotice> notices =
                bidService.notices(pageReq, "ACTIVE", null).getRecords();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.projlife.bid.entity.BidNotice n : notices) {
            Map<String, Object> row = new HashMap<>();
            row.put("noticeCode", n.getNoticeCode());
            row.put("title", n.getTitle());
            row.put("region", n.getRegion());
            row.put("budgetAmount", n.getBudgetAmount());
            row.put("deadlineAt", n.getDeadlineAt());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> demoRows(String domain, Map<String, Object> params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("domain", domain);
        row.put("keyword", params == null ? null : params.get("keyword"));
        row.put("deidentified", true);
        rows.add(row);
        return rows;
    }

    private Long requireLong(Map<String, Object> params, String key) {
        if (params == null || params.get(key) == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "参数 " + key + " 必填");
        }
        return Long.valueOf(String.valueOf(params.get(key)));
    }
}
