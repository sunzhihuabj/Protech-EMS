package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.org.entity.SysApproval;
import com.projlife.org.entity.SysApprovalNode;
import com.projlife.org.mapper.SysApprovalMapper;
import com.projlife.org.mapper.SysApprovalNodeMapper;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审批流引擎（FR-SYS-14，R8 P0 核心缺口）：
 * 提交→逐级审批→通过/驳回；支持转办/加签；待办按人查询。
 * 原子操作：每节点通过后 current_node +1；末节点通过 → 整单 APPROVED。
 * 回调：调用方通过 approvalType + targetId 路由到各自 Service 完成后续状态机。
 */
@Service
public class ApprovalService {

    private final SysApprovalMapper approvalMapper;
    private final SysApprovalNodeMapper nodeMapper;
    private final CodeSequenceService codeSequenceService;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalService(SysApprovalMapper approvalMapper, SysApprovalNodeMapper nodeMapper,
                           CodeSequenceService codeSequenceService, ApplicationEventPublisher eventPublisher) {
        this.approvalMapper = approvalMapper;
        this.nodeMapper = nodeMapper;
        this.codeSequenceService = codeSequenceService;
        this.eventPublisher = eventPublisher;
    }

    /** 发起审批：chain = [{nodeName, approverId}...] 按序逐级 */
    @RequirePermission("sys:approval:act")
    @Transactional
    public SysApproval submit(String approvalType, Long targetId, String targetDesc,
                              List<long[]> chain) {
        if (chain == null || chain.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "审批链不能为空");
        }
        SysApproval approval = new SysApproval();
        approval.setApprovalNo(codeSequenceService.next(new CodeTypes.CodeRequest(
                "AP:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                "AP-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-", 4)));
        approval.setApprovalType(approvalType);
        approval.setTargetId(targetId);
        approval.setTargetDesc(targetDesc);
        approval.setSubmitterId(UserContextHolder.currentUserId());
        approval.setStatus("PENDING");
        approval.setCurrentNode(1);
        approval.setTotalNodes(chain.size());
        approvalMapper.insert(approval);

        for (int i = 0; i < chain.size(); i++) {
            SysApprovalNode node = new SysApprovalNode();
            node.setApprovalId(approval.getId());
            node.setNodeSeq(i + 1);
            node.setNodeName("节点 " + (i + 1));
            node.setApproverId(chain.get(i)[1]);
            node.setStatus(i == 0 ? "PENDING" : "WAITING");
            nodeMapper.insert(node);
        }
        return approval;
    }

    /** 我的待办 */
    public List<SysApprovalNode> myPending() {
        Long uid = UserContextHolder.currentUserId();
        return nodeMapper.selectList(new LambdaQueryWrapper<SysApprovalNode>()
                .eq(SysApprovalNode::getApproverId, uid)
                .eq(SysApprovalNode::getStatus, "PENDING")
                .eq(SysApprovalNode::getDeleted, 0));
    }

    /** 通过当前节点：末节点通过 → 整单 APPROVED；否则推进到下一节点 */
    @RequirePermission("sys:approval:act")
    @Transactional
    public SysApprovalNode approve(Long nodeId, String comment) {
        SysApprovalNode node = mustNode(nodeId);
        mustBeCurrentPending(node);
        node.setStatus("APPROVED");
        node.setComment(comment);
        node.setActedAt(LocalDateTime.now());
        nodeMapper.updateById(node);

        SysApproval approval = mustApproval(node.getApprovalId());
        org.slf4j.MDC.put("approvalType", approval.getApprovalType());
        org.slf4j.MDC.put("targetId", String.valueOf(approval.getTargetId()));
        if (node.getNodeSeq() >= approval.getTotalNodes()) {
            approval.setStatus("APPROVED");
            eventPublisher.publishEvent(new ApprovalCompletedEvent(
                    approval.getApprovalType(), approval.getTargetId(), true));
        } else {
            approval.setCurrentNode(node.getNodeSeq() + 1);
            // 激活下一节点
            SysApprovalNode next = nodeMapper.selectOne(new LambdaQueryWrapper<SysApprovalNode>()
                    .eq(SysApprovalNode::getApprovalId, approval.getId())
                    .eq(SysApprovalNode::getNodeSeq, node.getNodeSeq() + 1)
                    .last("LIMIT 1"));
            if (next != null) {
                next.setStatus("PENDING");
                nodeMapper.updateById(next);
            }
        }
        approvalMapper.updateById(approval);
        return node;
    }

    /** 驳回：理由 ≥10 字（FR-CT-14），整单回退 REJECTED */
    @RequirePermission("sys:approval:act")
    @Transactional
    public SysApprovalNode reject(Long nodeId, String reason) {
        if (reason == null || reason.length() < 10) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "驳回理由须 ≥10 字（FR-CT-14）");
        }
        SysApprovalNode node = mustNode(nodeId);
        mustBeCurrentPending(node);
        node.setStatus("REJECTED");
        node.setComment(reason);
        node.setActedAt(LocalDateTime.now());
        nodeMapper.updateById(node);
        SysApproval approval = mustApproval(node.getApprovalId());
        org.slf4j.MDC.put("approvalType", approval.getApprovalType());
        org.slf4j.MDC.put("targetId", String.valueOf(approval.getTargetId()));
        approval.setStatus("REJECTED");
        approvalMapper.updateById(approval);
        eventPublisher.publishEvent(new ApprovalCompletedEvent(
                approval.getApprovalType(), approval.getTargetId(), false));
        return node;
    }

    /** 转办：当前节点 PENDING 改审批人 */
    @RequirePermission("sys:approval:act")
    @Transactional
    public SysApprovalNode delegate(Long nodeId, Long newApproverId) {
        SysApprovalNode node = mustNode(nodeId);
        if (!"PENDING".equals(node.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 PENDING 节点可转办");
        }
        node.setApproverId(newApproverId);
        nodeMapper.updateById(node);
        return node;
    }

    /** 加签：在当前节点后插入一个 PENDING 节点，后续节点序号 +1 */
    @RequirePermission("sys:approval:act")
    @Transactional
    public SysApprovalNode addSign(Long approvalId, Long addSignUserId) {
        SysApproval approval = mustApproval(approvalId);
        int insertSeq = approval.getCurrentNode() + 1;
        // 后续节点序号 +1
        List<SysApprovalNode> after = nodeMapper.selectList(new LambdaQueryWrapper<SysApprovalNode>()
                .eq(SysApprovalNode::getApprovalId, approvalId)
                .ge(SysApprovalNode::getNodeSeq, insertSeq));
        for (SysApprovalNode n : after) {
            n.setNodeSeq(n.getNodeSeq() + 1);
            nodeMapper.updateById(n);
        }
        SysApprovalNode newNode = new SysApprovalNode();
        newNode.setApprovalId(approvalId);
        newNode.setNodeSeq(insertSeq);
        newNode.setNodeName("加签");
        newNode.setApproverId(addSignUserId);
        newNode.setStatus("PENDING");
        nodeMapper.insert(newNode);
        approval.setTotalNodes(approval.getTotalNodes() + 1);
        approvalMapper.updateById(approval);
        return newNode;
    }

    /** 按目标单据查审批链（含节点明细） */
    public SysApproval findByTarget(String type, Long targetId) {
        return approvalMapper.selectOne(new LambdaQueryWrapper<SysApproval>()
                .eq(SysApproval::getApprovalType, type)
                .eq(SysApproval::getTargetId, targetId)
                .eq(SysApproval::getDeleted, 0).last("LIMIT 1"));
    }

    public List<SysApprovalNode> nodesOf(Long approvalId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<SysApprovalNode>()
                .eq(SysApprovalNode::getApprovalId, approvalId)
                .eq(SysApprovalNode::getDeleted, 0)
                .orderByAsc(SysApprovalNode::getNodeSeq));
    }

    private SysApproval mustApproval(Long id) {
        SysApproval a = approvalMapper.selectById(id);
        if (a == null) throw BizException.notFound("审批实例不存在: " + id);
        return a;
    }

    private SysApprovalNode mustNode(Long id) {
        SysApprovalNode n = nodeMapper.selectById(id);
        if (n == null) throw BizException.notFound("审批节点不存在: " + id);
        return n;
    }

    private void mustBeCurrentPending(SysApprovalNode node) {
        if (!"PENDING".equals(node.getStatus())) {
            throw BizException.conflict("该节点已处理（" + node.getStatus() + "）");
        }
        SysApproval approval = mustApproval(node.getApprovalId());
        if (approval.getCurrentNode() != node.getNodeSeq()) {
            throw BizException.conflict("非当前节点（当前 " + approval.getCurrentNode() + "，本节点 " + node.getNodeSeq() + "）");
        }
    }
}
