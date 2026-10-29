package com.projlife.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.contract.entity.CtContract;
import com.projlife.contract.entity.CtContractDispatch;
import com.projlife.contract.mapper.CtContractDispatchMapper;
import com.projlife.contract.mapper.CtContractMapper;
import com.projlife.common.spi.NotifyChannelAdapter;
import com.projlife.org.service.NotificationService;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同交底（04 册 FR-CT-28~30）：摘要分发 + 已阅确认 + 24h 未阅再提醒。
 */
@Service
public class ContractDispatchService {

    private final CtContractDispatchMapper dispatchMapper;
    private final CtContractMapper contractMapper;
    private final NotificationService notificationService;
    private final NotifyChannelAdapter channelAdapter;

    public ContractDispatchService(CtContractDispatchMapper dispatchMapper, CtContractMapper contractMapper,
                                   NotificationService notificationService, NotifyChannelAdapter channelAdapter) {
        this.dispatchMapper = dispatchMapper;
        this.contractMapper = contractMapper;
        this.notificationService = notificationService;
        this.channelAdapter = channelAdapter;
    }

    public List<CtContractDispatch> dispatchesOf(Long contractId) {
        return dispatchMapper.selectList(new LambdaQueryWrapper<CtContractDispatch>()
                .eq(CtContractDispatch::getContractId, contractId)
                .orderByDesc(CtContractDispatch::getId));
    }

    /** FR-CT-28/29：交底分发（接收人由前端按项目成员角色选择；站内 + 微信渠道） */
    @Audited(module = "contract", action = "dispatch_contract", targetType = "ct_contract", targetId = "{0}")
    @Transactional
    public int dispatch(Long contractId, String summary, List<Long> receiverIds) {
        CtContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw BizException.notFound("合同不存在");
        }
        String content = summary == null || summary.isEmpty() ? defaultSummary(contract) : summary;
        int count = 0;
        for (Long receiverId : receiverIds) {
            CtContractDispatch dispatch = new CtContractDispatch();
            dispatch.setContractId(contractId);
            dispatch.setReceiverId(receiverId);
            dispatch.setDispatchContent(content);
            dispatch.setSentAt(LocalDateTime.now());
            dispatch.setRemindCount(0);
            dispatchMapper.insert(dispatch);
            notificationService.send(receiverId, "CONTRACT_DISPATCH", "合同交底：" + contract.getContractName(),
                    content, "ct_contract", String.valueOf(contractId), "BOTH");
            count++;
        }
        return count;
    }

    /** FR-CT-30：已阅确认 */
    @Transactional
    public void confirmRead(Long dispatchId, Long receiverId) {
        CtContractDispatch dispatch = dispatchMapper.selectById(dispatchId);
        if (dispatch == null) {
            throw BizException.notFound("交底记录不存在");
        }
        if (!dispatch.getReceiverId().equals(receiverId)) {
            throw BizException.forbidden("只能确认自己的交底");
        }
        if (dispatch.getReadAt() == null) {
            dispatch.setReadAt(LocalDateTime.now());
            dispatchMapper.updateById(dispatch);
        }
    }

    /** FR-CT-30：24h 未阅二次提醒（job-worker 每日调用入口） */
    @Transactional
    public int remindUnread() {
        List<CtContractDispatch> unread = dispatchMapper.selectList(new LambdaQueryWrapper<CtContractDispatch>()
                .isNull(CtContractDispatch::getReadAt)
                .lt(CtContractDispatch::getSentAt, LocalDateTime.now().minusHours(24)));
        int count = 0;
        for (CtContractDispatch dispatch : unread) {
            dispatch.setRemindCount(dispatch.getRemindCount() + 1);
            dispatchMapper.updateById(dispatch);
            notificationService.send(dispatch.getReceiverId(), "CONTRACT_DISPATCH_REMIND",
                    "【再次提醒】合同交底待已阅", dispatch.getDispatchContent(),
                    "ct_contract", String.valueOf(dispatch.getContractId()), "INNER");
            count++;
        }
        return count;
    }

    /** FR-CT-28：交底摘要生成（金额脱敏口径：无 contract:view_price 的接收人界面再隐藏） */
    private String defaultSummary(CtContract contract) {
        return "项目/事项：" + contract.getContractName()
                + "；交货期：" + contract.getDeliveryDate()
                + "；质保期：" + (contract.getWarrantyMonths() == null ? "按合同" : contract.getWarrantyMonths() + " 个月")
                + "；验收标准：" + (contract.getAcceptanceClause() == null ? "按合同" : contract.getAcceptanceClause())
                + "；请及时阅读并确认。";
    }
}
