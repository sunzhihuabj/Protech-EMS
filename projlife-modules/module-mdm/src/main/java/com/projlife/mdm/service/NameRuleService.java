package com.projlife.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.mdm.entity.MdCustomer;
import com.projlife.mdm.entity.MdCustomerAlias;
import com.projlife.mdm.entity.MdProject;
import com.projlife.mdm.entity.MdProjectAlias;
import com.projlife.mdm.entity.MdWindFarm;
import com.projlife.mdm.mapper.MdCustomerAliasMapper;
import com.projlife.mdm.mapper.MdCustomerMapper;
import com.projlife.mdm.mapper.MdProjectAliasMapper;
import com.projlife.mdm.mapper.MdProjectMapper;
import com.projlife.mdm.mapper.MdWindFarmMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 名称规则服务（12 册 §2.3）：FR-MDM-12 三重查重 + FR-MDM-13 名称自动生成 + FR-MDM-14 禁止写法提醒。
 */
@Service
public class NameRuleService {

    /** 相似度阈值（FR-MDM-12 第二重） */
    private static final double SIMILAR_THRESHOLD = 0.6;

    private final MdProjectMapper projectMapper;
    private final MdProjectAliasMapper projectAliasMapper;
    private final MdWindFarmMapper farmMapper;
    private final MdCustomerMapper customerMapper;
    private final MdCustomerAliasMapper customerAliasMapper;

    public NameRuleService(MdProjectMapper projectMapper, MdProjectAliasMapper projectAliasMapper,
                           MdWindFarmMapper farmMapper, MdCustomerMapper customerMapper,
                           MdCustomerAliasMapper customerAliasMapper) {
        this.projectMapper = projectMapper;
        this.projectAliasMapper = projectAliasMapper;
        this.farmMapper = farmMapper;
        this.customerMapper = customerMapper;
        this.customerAliasMapper = customerAliasMapper;
    }

    /**
     * FR-MDM-13 名称自动生成。
     * 标准名 = 业主规范名－地名（去尾缀）－期次；期次空填"—"；口语名 = 风场简称（去尾缀）＋期次。
     */
    public Map<String, String> generateNames(String ownerName, String farmFullName, String phase, String orderType) {
        String farmCore = stripSuffix(farmFullName);
        String stdName = ownerName + "-" + farmCore + "-" + (phase == null || phase.isEmpty() ? "—" : phase);
        String shortName = farmCore + (phase == null || phase.isEmpty() ? "" : phase);
        Map<String, String> result = new HashMap<>();
        result.put("stdName", stdName);
        result.put("shortName", shortName);
        result.put("farmName", farmFullName);
        result.put("orderType", orderType);
        result.put("namingWarning", namingWarning(ownerName, farmFullName));
        return result;
    }

    /** FR-MDM-14：输入含地名但不含业主名 → 红字提醒 */
    public String namingWarning(String ownerName, String farmFullName) {
        if (ownerName == null || ownerName.isEmpty() || farmFullName == null || farmFullName.isEmpty()) {
            return null;
        }
        boolean farmHasLocation = farmFullName.contains("风场") || farmFullName.contains("风电场")
                || farmFullName.contains("风电站");
        boolean containsOwner = farmFullName.contains(ownerName)
                || (ownerName.length() >= 2 && farmFullName.contains(ownerName.substring(0, 2)));
        if (farmHasLocation && !containsOwner) {
            return "同一地名可能同时存在多家业主的风场，请补业主名（FR-MDM-14）";
        }
        return null;
    }

    /**
     * FR-MDM-12 三重查重（提交前依次）：
     * ① 风场全称精确（同名风场已有项目）；② 简称/别名相似度 ≥0.6；③ 同业主 + 同地域已有项目。
     * 命中任一返回对比卡列表，由用户选择"关联已有"或"确认新建（理由≥10 字）"。
     */
    public List<Map<String, Object>> tripleCheck(String farmFullName, String shortName, Long ownerId) {
        List<Map<String, Object>> hits = new ArrayList<>();

        // ① 风场全称精确
        if (farmFullName != null && !farmFullName.isEmpty()) {
            for (MdProject p : projectMapper.selectList(new LambdaQueryWrapper<MdProject>()
                    .eq(MdProject::getFarmNameRaw, farmFullName).last("LIMIT 5"))) {
                hits.add(hitCard(p, "FARM_NAME_EXACT", "风场全称精确命中"));
            }
        }

        // ② 常用简称 + 全部别名 相似度 ≥0.6
        if (shortName != null && !shortName.isEmpty()) {
            for (MdProject p : projectMapper.selectList(new LambdaQueryWrapper<MdProject>())) {
                if (similarity(shortName, p.getShortName()) >= SIMILAR_THRESHOLD) {
                    hits.add(hitCard(p, "SHORT_NAME_SIMILAR",
                            String.format("简称相似度 %.2f", similarity(shortName, p.getShortName()))));
                }
            }
            for (MdProjectAlias alias : projectAliasMapper.selectList(new LambdaQueryWrapper<MdProjectAlias>())) {
                if (similarity(shortName, alias.getAliasName()) >= SIMILAR_THRESHOLD) {
                    MdProject p = projectMapper.selectById(alias.getMdProjectId());
                    if (p != null) {
                        hits.add(hitCard(p, "ALIAS_SIMILAR",
                                String.format("别名「%s」相似度 %.2f", alias.getAliasName(), similarity(shortName, alias.getAliasName()))));
                    }
                }
            }
        }

        // ③ 业主 + 地名（同业主同地域风场的已有项目）
        if (ownerId != null && farmFullName != null && !farmFullName.isEmpty()) {
            List<MdWindFarm> ownerFarms = farmMapper.selectList(new LambdaQueryWrapper<MdWindFarm>()
                    .eq(MdWindFarm::getOwnerId, ownerId));
            for (MdWindFarm farm : ownerFarms) {
                boolean sameArea = farm.getFarmName() != null
                        && farmFullName.contains(stripSuffix(farm.getFarmName()));
                if (sameArea) {
                    for (MdProject p : projectMapper.selectList(new LambdaQueryWrapper<MdProject>()
                            .eq(MdProject::getFarmId, farm.getId()).last("LIMIT 5"))) {
                        hits.add(hitCard(p, "OWNER_LOCATION", "同业主同地域已有项目"));
                    }
                }
            }
        }
        return hits;
    }

    private String coreOf(String farmName) {
        return stripSuffix(farmName);
    }

    private Map<String, Object> hitCard(MdProject p, String rule, String reason) {
        Map<String, Object> card = new HashMap<>();
        card.put("projectId", p.getId());
        card.put("projectCode", p.getProjectCode());
        card.put("stdName", p.getStdName());
        card.put("shortName", p.getShortName());
        card.put("status", p.getStatus());
        card.put("rule", rule);
        card.put("reason", reason);
        return card;
    }

    /** 去尾缀：书名号/空格/「项目/工程/风电场/风场」等修饰词（FR-MDM-09） */
    public String stripSuffix(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replace("《", "").replace("》", "").trim();
        cleaned = cleaned.replaceAll("(风电场|风场|风电站|项目|工程)+$", "");
        cleaned = cleaned.replaceAll("\\s+", "");
        return cleaned;
    }

    /** 归一化 Levenshtein 相似度 */
    public double similarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        String s1 = a.toLowerCase();
        String s2 = b.toLowerCase();
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        int max = Math.max(s1.length(), s2.length());
        return max == 0 ? 1 : 1.0 - (double) dp[s1.length()][s2.length()] / max;
    }
}
