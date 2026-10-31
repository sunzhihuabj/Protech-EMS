package com.projlife.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.cost.entity.ArcArchive;
import com.projlife.cost.mapper.ArcArchiveMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 档案鉴定提醒（S11 遗留 → R6 落地，A-36 口径）。
 * 周期：P10=10 年 / P30=30 年 / PERM 跳过；到期前 1 年（365 天）产提醒。
 */
@Service
public class ArchiveAppraisalService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveAppraisalService.class);

    private final ArcArchiveMapper archiveMapper;

    public ArchiveAppraisalService(ArcArchiveMapper archiveMapper) {
        this.archiveMapper = archiveMapper;
    }

    private static final int REMIND_BEFORE_DAYS = 365;

    /**
     * 鉴定到期扫描：P10/P30 保管期限 + archive_year → 到期日 = archiveYear + years + 12-31；
     * 距到期 ≤365 天且状态非 DESTROYED → 产提醒
     */
    public List<ArcArchive> appraisalDue(int remindBeforeDays) {
        LocalDate now = LocalDate.now();
        List<ArcArchive> all = archiveMapper.selectList(new LambdaQueryWrapper<ArcArchive>()
                .eq(ArcArchive::getDeleted, 0)
                .ne(ArcArchive::getStatus, "DESTROYED"));
        LocalDate remindBefore = now.plusDays(remindBeforeDays > 0 ? remindBeforeDays : REMIND_BEFORE_DAYS);
        return all.stream().filter(a -> {
            Integer year = a.getArchiveYear();
            String retention = a.getRetention();
            if (year == null || retention == null || "PERM".equals(retention)) {
                return false;
            }
            int years = "P30".equals(retention) ? 30 : 10;
            LocalDate expiry = LocalDate.of(year + years, 12, 31);
            return !expiry.isBefore(now) && !expiry.isAfter(remindBefore);
        }).collect(java.util.stream.Collectors.toList());
    }

    /** 鉴定提醒扫描（job 入口，返回命中条数） */
    public int appraisalScan() {
        List<ArcArchive> due = appraisalDue(REMIND_BEFORE_DAYS);
        log.info("档案鉴定提醒扫描：{} 份档案距到期 ≤365 天", due.size());
        return due.size();
    }
}
