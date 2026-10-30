package com.projlife.bid.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.bid.entity.BidFeedback;
import com.projlife.bid.entity.BidMatch;
import com.projlife.bid.entity.BidNotice;
import com.projlife.bid.entity.BidPush;
import com.projlife.bid.entity.BidRaw;
import com.projlife.bid.entity.BidRule;
import com.projlife.bid.entity.BidSource;
import com.projlife.bid.entity.BidSubscribe;
import com.projlife.bid.mapper.BidFeedbackMapper;
import com.projlife.bid.mapper.BidMatchMapper;
import com.projlife.bid.mapper.BidNoticeMapper;
import com.projlife.bid.mapper.BidPushMapper;
import com.projlife.bid.mapper.BidRawMapper;
import com.projlife.bid.mapper.BidRuleMapper;
import com.projlife.bid.mapper.BidSourceMapper;
import com.projlife.bid.mapper.BidSubscribeMapper;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.crm.api.CrmApi;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 招标情报服务（V4-05 M18）：数据源管理/抓取去重/打分分级/一键转商机/推送订阅反馈。
 *
 * <p>FR-BID-01 数据源四类 A_OWNER/B_OEM/C_PORTAL/D_MANUAL；FR-BID-06 四层去重；
 * FR-BID-09 100 分制（25/25/15/15/10/10）；FR-BID-12 转商机 source_type=BID 写回。</p>
 */
@Service
public class BidService {

    private final BidSourceMapper sourceMapper;
    private final BidRawMapper rawMapper;
    private final BidNoticeMapper noticeMapper;
    private final BidMatchMapper matchMapper;
    private final BidRuleMapper ruleMapper;
    private final BidPushMapper pushMapper;
    private final BidSubscribeMapper subscribeMapper;
    private final BidFeedbackMapper feedbackMapper;
    private final CodeSequenceService codeSequenceService;
    private final CrmApi crmApi;

    public BidService(BidSourceMapper sourceMapper, BidRawMapper rawMapper, BidNoticeMapper noticeMapper,
                      BidMatchMapper matchMapper, BidRuleMapper ruleMapper, BidPushMapper pushMapper,
                      BidSubscribeMapper subscribeMapper, BidFeedbackMapper feedbackMapper,
                      CodeSequenceService codeSequenceService, CrmApi crmApi) {
        this.sourceMapper = sourceMapper;
        this.rawMapper = rawMapper;
        this.noticeMapper = noticeMapper;
        this.matchMapper = matchMapper;
        this.ruleMapper = ruleMapper;
        this.pushMapper = pushMapper;
        this.subscribeMapper = subscribeMapper;
        this.feedbackMapper = feedbackMapper;
        this.codeSequenceService = codeSequenceService;
        this.crmApi = crmApi;
    }

    // ---------- 数据源（FR-BID-01） ----------

    @RequirePermission("bid:source:manage")
    @Transactional
    public BidSource createSource(BidSource source) {
        if (source.getCategory() == null
                || !source.getCategory().matches("[ABCD]_(OWNER|OEM|PORTAL|MANUAL)")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "源类别必须为 A_OWNER/B_OEM/C_PORTAL/D_MANUAL");
        }
        if (source.getEntryUrl() == null || source.getEntryUrl().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "入口 URL 必填");
        }
        source.setSourceCode(codeSequenceService.next(CodeTypes.bidSource(
                source.getCategory().substring(0, 1))));
        if (source.getStatus() == null) {
            source.setStatus("PENDING_REVIEW");
        }
        sourceMapper.insert(source);
        return source;
    }

    /** 启用前必须 robots 检查留痕（FR-BID-02 合规）；禁抓站点不得启用 */
    @RequirePermission("bid:source:manage")
    @Transactional
    public BidSource reviewSource(Long id, String status, Long robotsStatusCode) {
        BidSource source = mustGetSource(id);
        if ("ACTIVE".equals(status)) {
            if (robotsStatusCode == null || robotsStatusCode != 200) {
                throw BizException.blocked("BID-ROBOTS",
                        "robots 未通过（非 200）不得启用抓取，请人工复核站点协议", null);
            }
            source.setRobotsCheckedAt(LocalDateTime.now());
        }
        source.setStatus(status);
        sourceMapper.updateById(source);
        return source;
    }

    // ---------- 抓取入库 + 四层去重（FR-BID-06） ----------

    /**
     * 入库：写原文 + 归一 URL + content_hash 去重 + 跨源合并/变更关联，返回公告与去重结果。
     * 层1 URL 归一去重；层2 content_hash UK；层3 跨源同业主同项目合并（EXACT_MERGED）；
     * 层4 变更/补遗 parent_id 关联（SUPERSEDED 原公告）。
     */
    @RequirePermission("bid:notice:view")
    @Transactional
    public Map<String, Object> ingest(Long sourceId, String url, String title, String content,
                                      String projectName, String lotNo, BigDecimal budgetAmount,
                                      LocalDateTime deadlineAt, String region, Long ownerId,
                                      String changeType) {
        BidSource source = mustGetSource(sourceId);
        if (!"ACTIVE".equals(source.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "数据源未启用，禁止抓取入库");
        }
        String normalizedUrl = normalizeUrl(url);
        String contentHash = sha256(normalizedUrl + "|" + (content == null ? title : content));

        Map<String, Object> result = new HashMap<>();
        // 层 2：content_hash 全局唯一（重复提交不受限频约束，幂等短路）
        BidNotice dupHash = noticeMapper.selectOne(new LambdaQueryWrapper<BidNotice>()
                .eq(BidNotice::getContentHash, contentHash).last("LIMIT 1"));
        if (dupHash != null) {
            result.put("dedup", "HASH_DUPLICATE");
            result.put("noticeId", dupHash.getId());
            result.put("noticeCode", dupHash.getNoticeCode());
            return result;
        }
        // 层 1：URL 归一去重
        BidNotice dupUrl = noticeMapper.selectOne(new LambdaQueryWrapper<BidNotice>()
                .eq(BidNotice::getUrl, normalizedUrl).last("LIMIT 1"));
        if (dupUrl != null) {
            result.put("dedup", "URL_DUPLICATE");
            result.put("noticeId", dupUrl.getId());
            result.put("noticeCode", dupUrl.getNoticeCode());
            return result;
        }
        // 同源频次控制：≥60s（FR-BID-03），仅对新抓取生效
        if (source.getLastWatermark() != null
                && source.getLastWatermark().isAfter(LocalDateTime.now().minusSeconds(60))
                && !"MANUAL".equals(source.getCategory())) {
            throw BizException.of(ErrorCode.TOO_MANY, "同源抓取间隔不得小于 60 秒（FR-BID-03）");
        }

        BidRaw raw = new BidRaw();
        raw.setSourceId(sourceId);
        raw.setBatchNo("BATCH-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + source.getId());
        raw.setUrl(normalizedUrl);
        raw.setHttpStatus(200);
        raw.setContentHash(contentHash);
        raw.setFetchedAt(LocalDateTime.now());
        rawMapper.insert(raw);

        // 层 3：跨源合并——同业主同项目同标段视为同一标的
        if (ownerId != null && projectName != null) {
            BidNotice merged = noticeMapper.selectOne(new LambdaQueryWrapper<BidNotice>()
                    .eq(BidNotice::getOwnerId, ownerId)
                    .eq(BidNotice::getProjectName, projectName)
                    .eq(lotNo != null, BidNotice::getLotNo, lotNo)
                    .eq(BidNotice::getStatus, "ACTIVE")
                    .ne(BidNotice::getSourceId, sourceId)
                    .last("LIMIT 1"));
            if (merged != null) {
                result.put("dedup", "EXACT_MERGED");
                result.put("noticeId", merged.getId());
                result.put("noticeCode", merged.getNoticeCode());
                return result;
            }
        }

        BidNotice notice = new BidNotice();
        notice.setNoticeCode(codeSequenceService.next(
                CodeTypes.bidNotice(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
        notice.setSourceId(sourceId);
        notice.setUrl(normalizedUrl);
        notice.setContentHash(contentHash);
        notice.setTitle(title);
        notice.setOwnerNameRaw(ownerId == null ? title : null);
        notice.setOwnerId(ownerId);
        notice.setProjectName(projectName);
        notice.setLotNo(lotNo);
        notice.setBudgetAmount(budgetAmount);
        notice.setDeadlineAt(deadlineAt);
        notice.setRegion(region);
        notice.setParseStatus("PARSED");
        notice.setParseBy("TEMPLATE");
        notice.setStatus("ACTIVE");
        // 层 4：变更/补遗关联原公告
        if (changeType != null && !changeType.isEmpty()) {
            BidNotice parent = noticeMapper.selectOne(new LambdaQueryWrapper<BidNotice>()
                    .eq(BidNotice::getProjectName, projectName)
                    .eq(BidNotice::getStatus, "ACTIVE")
                    .eq(BidNotice::getSourceId, sourceId)
                    .isNull(BidNotice::getParentId)
                    .last("LIMIT 1"));
            if (parent != null) {
                notice.setParentId(parent.getId());
                parent.setStatus("SUPERSEDED");
                noticeMapper.updateById(parent);
            }
        }
        noticeMapper.insert(notice);
        source.setLastWatermark(LocalDateTime.now());
        sourceMapper.updateById(source);

        scoreWithActiveRule(notice);
        result.put("dedup", "NEW");
        result.put("noticeId", notice.getId());
        result.put("noticeCode", notice.getNoticeCode());
        result.put("matchId", scoreOf(notice.getId()) == null ? null : scoreOf(notice.getId()).getId());
        return result;
    }

    // ---------- 打分与分级（FR-BID-09） ----------

    /** 100 分制：关键词 25/区域 25/金额 15/截止期 15/业主 10/机型 10；≥70 HIGH/50-69 MEDIUM/<50 ARCHIVED */
    @RequirePermission("bid:notice:view")
    @Transactional
    public BidMatch score(Long noticeId) {
        BidNotice notice = mustGetNotice(noticeId);
        return scoreWithActiveRule(notice);
    }

    private BidMatch scoreWithActiveRule(BidNotice notice) {
        BidRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<BidRule>()
                .eq(BidRule::getStatus, "ACTIVE").last("LIMIT 1"));
        String ruleVersion = rule == null ? "V1.0" : rule.getRuleVersion();
        List<String> positive = rule == null || rule.getKeywordPositive() == null
                ? defaultKeywords() : splitCsv(rule.getKeywordPositive());
        List<String> negative = rule == null || rule.getKeywordNegative() == null
                ? new ArrayList<String>() : splitCsv(rule.getKeywordNegative());
        List<String> regions = rule == null || rule.getRegionList() == null
                ? new ArrayList<String>() : splitCsv(rule.getRegionList());
        BigDecimal amountMin = rule == null ? null : rule.getAmountMin();
        BigDecimal amountMax = rule == null ? null : rule.getAmountMax();

        Map<String, Object> breakdown = new HashMap<>();
        String text = nvl(notice.getTitle()) + " " + nvl(notice.getProjectName())
                + " " + nvl(notice.getSummary());

        // 关键词 25：命中正向每个 +12.5（封顶 25），命中负向直接 0（BigDecimal，编码规范禁 double 金额/分值）
        BigDecimal keyword = BigDecimal.ZERO;
        boolean negHit = false;
        for (String kw : negative) {
            if (!kw.isEmpty() && text.contains(kw)) {
                negHit = true;
            }
        }
        if (!negHit) {
            for (String kw : positive) {
                if (!kw.isEmpty() && text.contains(kw)) {
                    keyword = keyword.add(new BigDecimal("12.5"));
                }
            }
        }
        keyword = keyword.min(new BigDecimal(25));
        breakdown.put("keyword", keyword);

        // 区域 25：命中白名单 25，白名单为空视为不限 15
        BigDecimal region = regions.isEmpty() ? new BigDecimal(15)
                : (notice.getRegion() != null && regions.contains(notice.getRegion())
                ? new BigDecimal(25) : BigDecimal.ZERO);
        breakdown.put("region", region);

        // 金额 15：在区间内 15，区间为空 10，区间外 0
        BigDecimal amount = new BigDecimal(10);
        if (amountMin != null || amountMax != null) {
            amount = BigDecimal.ZERO;
            BigDecimal budget = notice.getBudgetAmount();
            if (budget != null
                    && (amountMin == null || budget.compareTo(amountMin) >= 0)
                    && (amountMax == null || budget.compareTo(amountMax) <= 0)) {
                amount = new BigDecimal(15);
            }
        }
        breakdown.put("amount", amount);

        // 截止期 15：距截止 ≥14 天 15 / ≥7 天 10 / 其余 5 / 无截止 0
        BigDecimal deadline = BigDecimal.ZERO;
        if (notice.getDeadlineAt() != null) {
            long days = java.time.Duration.between(LocalDateTime.now(),
                    notice.getDeadlineAt()).toDays();
            deadline = days >= 14 ? new BigDecimal(15) : days >= 7 ? new BigDecimal(10) : new BigDecimal(5);
        }
        breakdown.put("deadline", deadline);

        // 业主 10：已归并到主体库 10
        BigDecimal owner = notice.getOwnerId() != null ? new BigDecimal(10) : BigDecimal.ZERO;
        breakdown.put("owner", owner);

        // 机型 10：标题含机型需求关键字（演示模板解析）
        BigDecimal turbine = text.contains("风机") || text.contains("机组") || text.contains("塔筒")
                ? new BigDecimal(10) : BigDecimal.ZERO;
        breakdown.put("turbine", turbine);

        BigDecimal total = keyword.add(region).add(amount).add(deadline).add(owner).add(turbine);
        BigDecimal score = total.setScale(2, RoundingMode.HALF_UP);
        String level = score.compareTo(new BigDecimal(70)) >= 0 ? "HIGH"
                : score.compareTo(new BigDecimal(50)) >= 0 ? "MEDIUM" : "ARCHIVED";

        BidMatch existing = matchMapper.selectOne(new LambdaQueryWrapper<BidMatch>()
                .eq(BidMatch::getNoticeId, notice.getId())
                .eq(BidMatch::getRuleVersion, ruleVersion).last("LIMIT 1"));
        if (existing == null) {
            existing = new BidMatch();
            existing.setNoticeId(notice.getId());
            existing.setRuleVersion(ruleVersion);
        }
        existing.setScore(score);
        existing.setLevel(level);
        // JSON 列要求合法 JSON（HashMap.toString 不是 JSON，MySQL 会拒收）
        try {
            existing.setBreakdown(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(breakdown));
        } catch (Exception e) {
            throw new IllegalStateException("breakdown 序列化失败", e);
        }
        if (existing.getId() == null) {
            matchMapper.insert(existing);
        } else {
            matchMapper.updateById(existing);
        }
        return existing;
    }

    // ---------- 一键转商机（FR-BID-12） ----------

    /** 预填 + source_type=BID 写回 + 公告置 CONVERTED；业主未归并时阻断引导 */
    @RequirePermission("bid:notice:view")
    @Transactional
    public Map<String, Object> convert(Long noticeId) {
        BidNotice notice = mustGetNotice(noticeId);
        if (!"ACTIVE".equals(notice.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "公告状态 " + notice.getStatus() + " 不可转商机");
        }
        if (notice.getOwnerId() == null) {
            throw BizException.blocked("BID-OWNER-UNMERGED",
                    "业主未归并到主体库，请先在客户主体完成归并后再转商机（FR-BID-12）", null);
        }
        String oppName = notice.getProjectName() == null ? notice.getTitle() : notice.getProjectName();
        Long oppId = crmApi.createOpportunityFromBid(oppName, notice.getOwnerId(),
                notice.getBudgetAmount(), notice.getSummary() == null ? notice.getTitle() : notice.getSummary(),
                notice.getId());
        notice.setStatus("CONVERTED");
        noticeMapper.updateById(notice);
        Map<String, Object> result = new HashMap<>();
        result.put("opportunityId", oppId);
        result.put("noticeId", notice.getId());
        result.put("sourceType", "BID");
        return result;
    }

    // ---------- 订阅 / 推送（FR-BID-10/11） ----------

    @RequirePermission("bid:push:subscribe")
    @Transactional
    public BidSubscribe saveSubscribe(BidSubscribe subscribe) {
        if (subscribe.getUserId() == null) {
            subscribe.setUserId(UserContextHolderUserId());
        }
        BidSubscribe existing = subscribeMapper.selectOne(new LambdaQueryWrapper<BidSubscribe>()
                .eq(BidSubscribe::getUserId, subscribe.getUserId()).last("LIMIT 1"));
        if (existing != null) {
            subscribe.setId(existing.getId());
            subscribeMapper.updateById(subscribe);
        } else {
            subscribeMapper.insert(subscribe);
        }
        return subscribe;
    }

    /** 推送给匹配订阅条件的用户（INNER 站内演示；DINGTALK 随生产批次） */
    @RequirePermission("bid:notice:view")
    @Transactional
    public Map<String, Object> push(Long noticeId) {
        BidNotice notice = mustGetNotice(noticeId);
        List<BidSubscribe> subs = subscribeMapper.selectList(new LambdaQueryWrapper<BidSubscribe>()
                .eq(BidSubscribe::getDeleted, 0));
        int sent = 0;
        List<Long> receivers = new ArrayList<>();
        for (BidSubscribe sub : subs) {
            if (matchSubscribe(sub, notice)) {
                BidPush push = new BidPush();
                push.setNoticeId(noticeId);
                push.setReceiverId(sub.getUserId());
                push.setChannel("INNER");
                push.setSendStatus("SENT");
                push.setSentAt(LocalDateTime.now());
                try {
                    pushMapper.insert(push);
                    sent++;
                    receivers.add(sub.getUserId());
                } catch (org.springframework.dao.DuplicateKeyException ignore) {
                    // 已推送过（UK notice+receiver+channel），幂等跳过
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("noticeId", noticeId);
        result.put("sent", sent);
        result.put("receivers", receivers);
        return result;
    }

    private boolean matchSubscribe(BidSubscribe sub, BidNotice notice) {
        if (sub.getKeywords() != null) {
            String text = nvl(notice.getTitle()) + " " + nvl(notice.getProjectName());
            boolean hit = false;
            for (String kw : splitCsv(sub.getKeywords())) {
                if (!kw.isEmpty() && text.contains(kw)) {
                    hit = true;
                }
            }
            if (!hit) {
                return false;
            }
        }
        if (sub.getRegions() != null && notice.getRegion() != null
                && !splitCsv(sub.getRegions()).contains(notice.getRegion())) {
            return false;
        }
        if (sub.getAmountMin() != null
                && (notice.getBudgetAmount() == null
                || notice.getBudgetAmount().compareTo(sub.getAmountMin()) < 0)) {
            return false;
        }
        return true;
    }

    // ---------- 反馈回训（FR-BID-13/14） ----------

    @RequirePermission("bid:notice:manual")
    @Transactional
    public Long feedback(BidFeedback feedback) {
        if (feedback.getNoticeId() == null || feedback.getFeedbackType() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "公告与反馈类型必填");
        }
        mustGetNotice(feedback.getNoticeId());
        if (!feedback.getFeedbackType().matches("EFFECTIVE|IRRELEVANT|BID_RESULT")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "反馈类型必须为 EFFECTIVE/IRRELEVANT/BID_RESULT");
        }
        if (feedback.getOperatorId() == null) {
            feedback.setOperatorId(UserContextHolderUserId());
        }
        feedbackMapper.insert(feedback);
        // 回训：EFFECTIVE/IRRELEVANT 校准打分 is_correct
        BidMatch match = scoreOf(feedback.getNoticeId());
        if (match != null) {
            match.setIsCorrect("IRRELEVANT".equals(feedback.getFeedbackType()) ? 0 : 1);
            matchMapper.updateById(match);
        }
        return feedback.getId();
    }

    // ---------- 查询 ----------

    public PageResp<BidSource> sources(PageReq pageReq, String status) {
        Page<BidSource> page = sourceMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()),
                new LambdaQueryWrapper<BidSource>().eq(status != null, BidSource::getStatus, status)
                        .orderByDesc(BidSource::getId));
        return PageResp.of(page.getTotal(), page.getRecords());
    }

    public PageResp<BidNotice> notices(PageReq pageReq, String status, String level) {
        LambdaQueryWrapper<BidNotice> wrapper = new LambdaQueryWrapper<BidNotice>()
                .eq(status != null, BidNotice::getStatus, status)
                .orderByDesc(BidNotice::getId);
        if (level != null) {
            List<BidMatch> matches = matchMapper.selectList(new LambdaQueryWrapper<BidMatch>()
                    .eq(BidMatch::getLevel, level));
            List<Long> noticeIds = new ArrayList<>();
            for (BidMatch m : matches) {
                noticeIds.add(m.getNoticeId());
            }
            if (noticeIds.isEmpty()) {
                return PageResp.of(0, new ArrayList<BidNotice>());
            }
            wrapper.in(BidNotice::getId, noticeIds);
        }
        Page<BidNotice> page = noticeMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        return PageResp.of(page.getTotal(), page.getRecords());
    }

    public BidNotice noticeDetail(Long id) {
        return mustGetNotice(id);
    }

    public BidMatch matchOf(Long noticeId) {
        return scoreOf(noticeId);
    }

    // ---------- 内部 ----------

    private BidSource mustGetSource(Long id) {
        BidSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw BizException.notFound("数据源不存在: " + id);
        }
        return source;
    }

    private BidNotice mustGetNotice(Long id) {
        BidNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw BizException.notFound("公告不存在: " + id);
        }
        return notice;
    }

    private BidMatch scoreOf(Long noticeId) {
        return matchMapper.selectOne(new LambdaQueryWrapper<BidMatch>()
                .eq(BidMatch::getNoticeId, noticeId).last("LIMIT 1"));
    }

    private Long UserContextHolderUserId() {
        return com.projlife.common.context.UserContextHolder.currentUserId();
    }

    private List<String> defaultKeywords() {
        List<String> kws = new ArrayList<>();
        kws.add("风电");
        kws.add("风机");
        kws.add("塔筒");
        kws.add("风电场");
        return kws;
    }

    private List<String> splitCsv(String json) {
        List<String> list = new ArrayList<>();
        for (String s : json.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
            if (!s.trim().isEmpty()) {
                list.add(s.trim());
            }
        }
        return list;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /** URL 归一：小写、去默认端口、去 fragment、去 utm_ 与 spm 追踪参数、去尾部斜杠（FR-BID-06 层 1） */
    static String normalizeUrl(String url) {
        String u = url.trim().toLowerCase(Locale.ROOT);
        u = u.replaceFirst("^http://", "https://");
        u = u.replaceAll(":443/", "/");
        int hash = u.indexOf('#');
        if (hash >= 0) {
            u = u.substring(0, hash);
        }
        int q = u.indexOf('?');
        if (q >= 0) {
            StringBuilder kept = new StringBuilder();
            for (String pair : u.substring(q + 1).split("&")) {
                if (pair.isEmpty() || pair.startsWith("utm_") || pair.startsWith("spm")) {
                    continue;
                }
                if (kept.length() > 0) {
                    kept.append('&');
                }
                kept.append(pair);
            }
            u = kept.length() == 0 ? u.substring(0, q) : u.substring(0, q + 1) + kept;
        }
        while (u.endsWith("/") && u.length() > "https://a".length()) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
