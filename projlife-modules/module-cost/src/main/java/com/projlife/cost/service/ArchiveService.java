package com.projlife.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.cost.entity.ArcArchive;
import com.projlife.cost.entity.ArcBorrow;
import com.projlife.cost.entity.ArcDestroy;
import com.projlife.cost.entity.ArcTransfer;
import com.projlife.cost.mapper.ArcArchiveMapper;
import com.projlife.cost.mapper.ArcBorrowMapper;
import com.projlife.cost.mapper.ArcDestroyMapper;
import com.projlife.cost.mapper.ArcTransferMapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.context.UserContextHolder;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 档案管理服务（V4-02 M13 T-ARC）：借阅/归还/销毁/移交/逾期扫描。
 * 销毁必须有鉴定意见；在借档案不得销毁/重复借出。
 */
@Service
public class ArchiveService {

    private final ArcArchiveMapper archiveMapper;
    private final ArcBorrowMapper borrowMapper;
    private final ArcDestroyMapper destroyMapper;
    private final ArcTransferMapper transferMapper;
    private final CodeSequenceService codeSequenceService;

    public ArchiveService(ArcArchiveMapper archiveMapper, ArcBorrowMapper borrowMapper,
                          ArcDestroyMapper destroyMapper, ArcTransferMapper transferMapper,
                          CodeSequenceService codeSequenceService) {
        this.archiveMapper = archiveMapper;
        this.borrowMapper = borrowMapper;
        this.destroyMapper = destroyMapper;
        this.transferMapper = transferMapper;
        this.codeSequenceService = codeSequenceService;
    }

    /** 建档：PRT-{YYYY}-{门类}-{期限}-{4位} */
    @RequirePermission("arc:manage")
    @Transactional
    public ArcArchive create(ArcArchive archive) {
        if (archive.getTitle() == null || archive.getTitle().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "题名必填");
        }
        if (archive.getCategory() == null || archive.getRetention() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "门类（E-41）与保管期限（E-42）必填");
        }
        String year = String.valueOf(archive.getArchiveYear() == null
                ? LocalDate.now().getYear() : archive.getArchiveYear());
        archive.setArchiveNo(codeSequenceService.next(
                CodeTypes.archive("PRT", year, archive.getCategory(), archive.getRetention())));
        if (archive.getStatus() == null) {
            archive.setStatus("IN");
        }
        archiveMapper.insert(archive);
        return archive;
    }

    /** 借阅：在借档案不得重复借出 */
    @RequirePermission("arc:manage")
    @Transactional
    public ArcBorrow borrow(ArcBorrow borrow) {
        ArcArchive archive = mustArchive(borrow.getArchiveId());
        if (!"IN".equals(archive.getStatus())) {
            throw BizException.blocked("ARC-NOT-IN",
                    "档案当前状态 " + archive.getStatus() + " 不可借出", "请先归还或在档案台账确认状态");
        }
        Long outs = borrowMapper.selectCount(new LambdaQueryWrapper<ArcBorrow>()
                .eq(ArcBorrow::getArchiveId, borrow.getArchiveId())
                .eq(ArcBorrow::getStatus, "OUT").eq(ArcBorrow::getDeleted, 0));
        if (outs != null && outs > 0) {
            throw BizException.conflict("该档案已有在借记录，不得重复借出");
        }
        if (borrow.getDueDate() == null || borrow.getBorrowDate() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "借出日与应还日必填");
        }
        if (borrow.getApproverId() == null) {
            borrow.setApproverId(UserContextHolder.currentUserId()); // 审批人默认当前操作人（部门主管审批随流程批次）
        }
        if (borrow.getDueDate().isBefore(borrow.getBorrowDate())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "应还日不得早于借出日");
        }
        borrow.setBorrowNo(codeSequenceService.next(CodeTypes.archiveBorrow(
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
        borrow.setStatus("OUT");
        borrowMapper.insert(borrow);
        return borrow;
    }

    /** SPRINT-UID-R15：在借/全部借阅单列表（归还入口数据源，仅 OUT/OVERDUE 时传 status） */
    @RequirePermission("arc:manage")
    public List<ArcBorrow> borrows(String status) {
        LambdaQueryWrapper<ArcBorrow> wrapper = new LambdaQueryWrapper<ArcBorrow>()
                .eq(ArcBorrow::getDeleted, 0)
                .orderByDesc(ArcBorrow::getId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ArcBorrow::getStatus, status);
        }
        return borrowMapper.selectList(wrapper);
    }

    /** 归还 */
    @RequirePermission("arc:manage")
    @Transactional
    public ArcBorrow giveBack(Long borrowId) {
        ArcBorrow borrow = borrowMapper.selectById(borrowId);
        if (borrow == null) {
            throw BizException.notFound("借阅单不存在: " + borrowId);
        }
        if (!"OUT".equals(borrow.getStatus()) && !"OVERDUE".equals(borrow.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 OUT/OVERDUE 可归还");
        }
        borrow.setStatus("RETURNED");
        borrow.setReturnDate(LocalDate.now());
        borrowMapper.updateById(borrow);
        return borrow;
    }

    /** 销毁：鉴定意见必填（04 册档案鉴定）；在借档案不得销毁 */
    @RequirePermission("arc:manage")
    @Transactional
    public ArcDestroy destroy(ArcDestroy destroy) {
        if (destroy.getArchiveIds() == null || destroy.getArchiveIds().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "销毁档案清单必填");
        }
        if (destroy.getReason() == null || destroy.getReason().isEmpty()
                || destroy.getAppraisalOpinion() == null || destroy.getAppraisalOpinion().isEmpty()) {
            throw BizException.blocked("ARC-NO-APPRAISAL",
                    "销毁必须填写销毁原因与鉴定意见", "请先完成档案鉴定流程（04 册 §档案鉴定）");
        }
        for (String idText : destroy.getArchiveIds().split(",")) {
            ArcArchive archive = mustArchive(Long.valueOf(idText.trim()));
            if ("DESTROYED".equals(archive.getStatus())) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "档案已销毁: " + idText);
            }
            Long outs = borrowMapper.selectCount(new LambdaQueryWrapper<ArcBorrow>()
                    .eq(ArcBorrow::getArchiveId, archive.getId())
                    .eq(ArcBorrow::getStatus, "OUT").eq(ArcBorrow::getDeleted, 0));
            if (outs != null && outs > 0) {
                throw BizException.blocked("ARC-BORROWED",
                        "档案在借中不得销毁: " + idText, "请先归还再发起销毁");
            }
        }
        if (destroy.getApprovedBy() == null) {
            destroy.setApprovedBy(UserContextHolder.currentUserId());
        }
        destroy.setDestroyNo(codeSequenceService.next(CodeTypes.archiveDestroy(
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
        destroyMapper.insert(destroy);
        for (String idText : destroy.getArchiveIds().split(",")) {
            ArcArchive archive = mustArchive(Long.valueOf(idText.trim()));
            archive.setStatus("DESTROYED");
            archiveMapper.updateById(archive);
        }
        return destroy;
    }

    /** 移交发起 */
    @RequirePermission("arc:manage")
    @Transactional
    public ArcTransfer transfer(ArcTransfer transfer) {
        if (transfer.getArchiveIds() == null || transfer.getArchiveIds().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "移交档案清单必填");
        }
        transfer.setTransferNo(codeSequenceService.next(CodeTypes.archiveTransfer(
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
        transfer.setStatus("PENDING");
        transferMapper.insert(transfer);
        return transfer;
    }

    /** 移交确认：档案归入接收部门 */
    @RequirePermission("arc:manage")
    @Transactional
    public ArcTransfer confirmTransfer(Long id) {
        ArcTransfer transfer = transferMapper.selectById(id);
        if (transfer == null) {
            throw BizException.notFound("移交单不存在: " + id);
        }
        if (!"PENDING".equals(transfer.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "移交单已确认");
        }
        transfer.setStatus("DONE");
        transferMapper.updateById(transfer);
        for (String idText : transfer.getArchiveIds().split(",")) {
            ArcArchive archive = mustArchive(Long.valueOf(idText.trim()));
            archive.setDeptId(transfer.getToDept());
            archiveMapper.updateById(archive);
        }
        return transfer;
    }

    /** 逾期扫描（job 入口）：OUT 且应还日已过 → OVERDUE */
    @Transactional
    public int overdueScan() {
        List<ArcBorrow> outs = borrowMapper.selectList(new LambdaQueryWrapper<ArcBorrow>()
                .eq(ArcBorrow::getStatus, "OUT").eq(ArcBorrow::getDeleted, 0));
        int n = 0;
        for (ArcBorrow borrow : outs) {
            if (borrow.getDueDate() != null && borrow.getDueDate().isBefore(LocalDate.now())) {
                borrow.setStatus("OVERDUE");
                borrowMapper.updateById(borrow);
                n++;
            }
        }
        return n;
    }

    private ArcArchive mustArchive(Long id) {
        ArcArchive archive = archiveMapper.selectById(id);
        if (archive == null) {
            throw BizException.notFound("档案不存在: " + id);
        }
        return archive;
    }
}
