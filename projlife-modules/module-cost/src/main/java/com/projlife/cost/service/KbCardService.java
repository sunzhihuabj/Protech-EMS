package com.projlife.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.cost.entity.KbCard;
import com.projlife.cost.entity.KbCardLog;
import com.projlife.cost.entity.KbEscalation;
import com.projlife.cost.mapper.KbCardLogMapper;
import com.projlife.cost.mapper.KbCardMapper;
import com.projlife.cost.mapper.KbEscalationMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 看板卡片（V4-05 M12：FR-KB-04/05/06/07）。
 * 五要素缺一不许上板；VAGUE 拦截；红灯卡点必填；红灯 3 天自动升级。
 */
@Service
public class KbCardService {

    /** VAGUE 词表（FR-KB-05）：无交付物表述拒绝 */
    private static final Pattern VAGUE = Pattern.compile("^(跟进|推进|持续|加强|关注|协调).*");

    private final KbCardMapper cardMapper;
    private final KbCardLogMapper logMapper;
    private final KbEscalationMapper escalationMapper;
    private final CodeSequenceService codeSequenceService;

    public KbCardService(KbCardMapper cardMapper, KbCardLogMapper logMapper,
                         KbEscalationMapper escalationMapper, CodeSequenceService codeSequenceService) {
        this.cardMapper = cardMapper;
        this.logMapper = logMapper;
        this.escalationMapper = escalationMapper;
        this.codeSequenceService = codeSequenceService;
    }

    public PageResp<KbCard> page(PageReq pageReq, Long boardId, String status) {
        LambdaQueryWrapper<KbCard> wrapper = new LambdaQueryWrapper<KbCard>()
                .orderByDesc(KbCard::getId);
        if (boardId != null) {
            wrapper.eq(KbCard::getBoardId, boardId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(KbCard::getStatus, status);
        }
        Page<KbCard> page = cardMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<KbCard> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** FR-KB-04 五要素校验 + FR-KB-05 VAGUE 拦截（UC-KB-01） */
    @Audited(module = "kb", action = "create_card", targetType = "kb_card", targetId = "{0.cardNo}")
    @Transactional
    public Long create(KbCard card) {
        if (card.getBoardId() == null || card.getTitle() == null || card.getTitle().isEmpty()
                || card.getOwnerId() == null || card.getPromiseDate() == null) {
            throw BizException.badRequest("五要素缺一不许上板（FR-KB-04）：事项/责任人/承诺日/状态/卡点说明");
        }
        if (VAGUE.matcher(card.getTitle().trim()).find() && card.getTitle().trim().length() <= 12) {
            throw BizException.of(com.projlife.common.exception.ErrorCode.PARAM_INVALID,
                    "表述模糊（VAGUE），请改写为含交付物的具体事项").with("blockCode", "SYS-KB-VAGUE");
        }
        card.setCardNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "CARD:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "CARD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-", 3)));
        card.setStatus("TODO");
        card.setCreatedBy(UserContextHolder.currentUserName());
        cardMapper.insert(card);
        logTransition(card.getId(), "CREATE", "TODO", null);
        return card.getId();
    }

    /** FR-KB-06：红灯（BLOCKED）必填卡点说明（UC-KB-02） */
    @Audited(module = "kb", action = "block_card", targetType = "kb_card", targetId = "{0}")
    @Transactional
    public void block(Long cardId, String blockReason) {
        KbCard card = mustGet(cardId);
        if (blockReason == null || blockReason.trim().length() < 5) {
            throw BizException.of(com.projlife.common.exception.ErrorCode.BLOCKED, "红灯卡点说明必填（≥5 字，UC-KB-02）")
                    .with("blockCode", "SYS-KB-BLOCK-REASON");
        }
        String from = card.getStatus();
        card.setStatus("BLOCKED");
        card.setBlockReason(blockReason);
        cardMapper.updateById(card);
        logTransition(cardId, from, "BLOCKED", blockReason);
    }

    /** FR-KB-07：红灯 3 天未解 → 自动升级上总板（UC-KB-03）；job 入口 */
    @Audited(module = "kb", action = "escalate_red_cards", targetType = "kb_card", targetId = "batch")
    @Transactional
    public int escalateRedCards() {
        List<KbCard> reds = cardMapper.selectList(new LambdaQueryWrapper<KbCard>()
                .eq(KbCard::getStatus, "BLOCKED")
                .lt(KbCard::getUpdatedAt, LocalDateTime.now().minusDays(3)));
        int count = 0;
        for (KbCard card : reds) {
            card.setStatus("ESCALATED");
            cardMapper.updateById(card);
            logTransition(card.getId(), "BLOCKED", "ESCALATED", "红灯超 3 天自动升级上总板");
            KbEscalation escalation = new KbEscalation();
            escalation.setCardId(card.getId());
            escalation.setLevel("DAY3");
            escalation.setEscalatedAt(LocalDateTime.now());
            escalationMapper.insert(escalation);
            count++;
        }
        return count;
    }

    /** FR-KB-10：三问过堂（卡在哪/谁解决/何时解决）→ DONE 闭环 */
    @Audited(module = "kb", action = "close_card", targetType = "kb_card", targetId = "{0}")
    @Transactional
    public void close(Long cardId, String solution) {
        KbCard card = mustGet(cardId);
        if (solution == null || solution.trim().length() < 5) {
            throw BizException.badRequest("三问过堂：卡在哪/谁解决/何时解决——未填写视为未闭环（UC-KB-06）");
        }
        String from = card.getStatus();
        card.setStatus("DONE");
        card.setBlockReason(solution);
        cardMapper.updateById(card);
        logTransition(cardId, from, "DONE", solution);
    }

    /** FR-KB-13：补录率校验（补录 ÷ 新增 ≤ 5%） */
    public BigDecimal backfillRate() {
        long total = cardMapper.selectCount(new LambdaQueryWrapper<KbCard>());
        long backfill = cardMapper.selectCount(new LambdaQueryWrapper<KbCard>()
                .eq(KbCard::getIsBackfill, 1));
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(backfill * 100L / total);
    }

    private void logTransition(Long cardId, String from, String to, String remark) {
        KbCardLog logRow = new KbCardLog();
        logRow.setCardId(cardId);
        logRow.setFromStatus(from);
        logRow.setToStatus(to);
        logRow.setAction(to);
        logRow.setRemark(remark);
        logRow.setOperatorId(UserContextHolder.currentUserId() == null ? 0L : UserContextHolder.currentUserId());
        logMapper.insert(logRow);
    }

    private KbCard mustGet(Long id) {
        KbCard card = cardMapper.selectById(id);
        if (card == null) {
            throw BizException.notFound("卡片不存在");
        }
        return card;
    }
}
