package com.projlife.contract.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.contract.service.ContractDispatchService;
import com.projlife.contract.service.ContractFinanceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合同内部任务端点（SPRINT-UID-R18）：/api/internal/jobs/** 由 InternalKeyFilter 共享密钥门守卫
 * （permitAll + X-Internal-Key，A-46 口径），由 projlife-job 经 InternalJobInvoker 定时调用。
 * <ul>
 *   <li>remind-unread：交底 24h 未读再次提醒（FR-CT-10，remindUnread 死方法转正）</li>
 *   <li>payment-overdue-remind：逾期收款计划按合同创建人提醒（FR-CT-38，overduePlans 死方法转正）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/internal/jobs/contract")
public class InternalContractJobsController {

    private final ContractDispatchService dispatchService;
    private final ContractFinanceService financeService;

    public InternalContractJobsController(ContractDispatchService dispatchService,
                                          ContractFinanceService financeService) {
        this.dispatchService = dispatchService;
        this.financeService = financeService;
    }

    /** 交底未读再提醒（24h 未读 +1 次），返回提醒条数 */
    @PostMapping("/remind-unread")
    public ApiResponse<Integer> remindUnread() {
        return ApiResponse.ok(dispatchService.remindUnread());
    }

    /** 逾期收款计划提醒，返回提醒计划行数 */
    @PostMapping("/payment-overdue-remind")
    public ApiResponse<Integer> paymentOverdueRemind() {
        return ApiResponse.ok(financeService.remindOverduePlans());
    }
}
