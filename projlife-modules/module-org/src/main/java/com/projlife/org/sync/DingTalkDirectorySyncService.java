package com.projlife.org.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.projlife.common.spi.IntegrationAuditRecorder;
import com.projlife.integration.notify.DingTalkClient;
import com.projlife.integration.notify.DingTalkConfig;
import com.projlife.org.entity.SysDingtalkBinding;
import com.projlife.org.entity.SysUser;
import com.projlife.org.mapper.SysDingtalkBindingMapper;
import com.projlife.org.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 钉钉通讯录同步服务（SPRINT-INT2，报告①§2.1）：
 * BFS 部门树（根=1）→ 逐部门分页拉用户详情（userid/unionid/name/mobile）→ 绑定表 upsert → 匹配 sys_user。
 * 匹配顺序（A-44）：手机号（精确）→ 姓名唯一 → NONE；MANUAL 绑定不被同步覆盖。
 * 护栏：部门 ≤500 / 用户 ≤5000 / 深度 ≤10，防异常数据拖垮调用（钉钉限流）。
 * 审计：按运行汇总落 int_sync_log（PULL），逐调用不落。
 */
@Service
public class DingTalkDirectorySyncService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkDirectorySyncService.class);

    static final int MAX_DEPTS = 500;
    static final int MAX_USERS = 5000;
    static final int MAX_DEPTH = 10;
    static final int PAGE_SIZE = 100;

    private final DingTalkConfig dingTalkConfig;
    private final DingTalkClient dingTalkClient;
    private final SysDingtalkBindingMapper bindingMapper;
    private final SysUserMapper userMapper;

    @Autowired(required = false)
    private IntegrationAuditRecorder auditRecorder;

    public DingTalkDirectorySyncService(DingTalkConfig dingTalkConfig, DingTalkClient dingTalkClient,
                                        SysDingtalkBindingMapper bindingMapper, SysUserMapper userMapper) {
        this.dingTalkConfig = dingTalkConfig;
        this.dingTalkClient = dingTalkClient;
        this.bindingMapper = bindingMapper;
        this.userMapper = userMapper;
    }

    /** 同步结果汇总 */
    public static class SyncSummary {
        public boolean ok;
        public String reason;
        public int depts;
        public int users;
        public int bound;
        public int manualKept;
        public List<String> unmatchedNames = new ArrayList<>();
    }

    public SyncSummary syncAll() {
        SyncSummary s = new SyncSummary();
        if (!dingTalkConfig.tokenConfigured()) {
            s.reason = "钉钉凭据未配置（appKey/appSecret）";
            audit("FAILED", s.reason, null);
            return s;
        }

        // 1) BFS 部门树
        List<Long> deptIds = new ArrayList<>();
        deptIds.add(1L);
        Queue<long[]> queue = new LinkedList<>(); // [deptId, depth]
        queue.add(new long[]{1L, 1});
        Set<Long> visited = new HashSet<>();
        visited.add(1L);
        boolean truncated = false;
        while (!queue.isEmpty()) {
            long[] cur = queue.poll();
            long deptId = cur[0];
            int depth = (int) cur[1];
            if (depth > MAX_DEPTH || deptIds.size() >= MAX_DEPTS) {
                truncated = true;
                break;
            }
            DingTalkClient.ApiResult r = dingTalkClient.listSubDepartments(deptId);
            if (!r.ok) {
                s.reason = r.reason;
                audit("FAILED", s.reason, null);
                return s;
            }
            for (JsonNode sub : r.data) {
                long subId = sub.path("dept_id").asLong(sub.path("deptId").asLong(0));
                if (subId <= 0 || !visited.add(subId)) {
                    continue;
                }
                deptIds.add(subId);
                queue.add(new long[]{subId, depth + 1});
            }
        }
        s.depts = deptIds.size();

        // 2) 逐部门分页拉用户
        Map<String, JsonNode> users = new LinkedHashMap<>(); // userid → detail（跨部门去重）
        for (Long deptId : deptIds) {
            long cursor = 0;
            for (int page = 0; page < 100; page++) {
                DingTalkClient.ApiResult r = dingTalkClient.listDeptUsers(deptId, cursor);
                if (!r.ok) {
                    s.reason = r.reason;
                    audit("FAILED", s.reason, null);
                    return s;
                }
                for (JsonNode u : r.data.path("list")) {
                    String userid = u.path("userid").asText("");
                    if (userid.isEmpty() || users.containsKey(userid)) {
                        continue;
                    }
                    users.put(userid, u);
                    if (users.size() >= MAX_USERS) {
                        truncated = true;
                        break;
                    }
                }
                if (truncated || !r.data.path("has_more").asBoolean(false)) {
                    break;
                }
                cursor = r.data.path("next_cursor").asLong(0);
            }
            if (truncated) {
                break;
            }
        }
        s.users = users.size();

        // 3) 绑定表 upsert + 匹配
        List<SysUser> sysUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0));
        for (Map.Entry<String, JsonNode> e : users.entrySet()) {
            String userid = e.getKey();
            JsonNode u = e.getValue();
            SysDingtalkBinding existing = findExisting(userid);
            boolean created = existing == null;
            SysDingtalkBinding b = existing == null ? new SysDingtalkBinding() : existing;
            b.setDingtalkUserid(userid);
            b.setUnionId(textOrNull(u.path("unionid")));
            b.setName(textOrNull(u.path("name")));
            b.setSyncedAt(LocalDateTime.now());
            if (created) {
                b.setDeleted(0);
                b.setCreatedBy("dirsync");
                b.setVersion(0);
            }

            // MANUAL 绑定保留（A-44）；否则按 MOBILE → NAME(唯一) 匹配
            if (!created && "MANUAL".equals(b.getMatchType()) && b.getSysUserId() != null) {
                s.manualKept++;
            } else {
                match(b, u, sysUsers);
                if ("NONE".equals(b.getMatchType()) && b.getName() != null
                        && s.unmatchedNames.size() < 50) {
                    s.unmatchedNames.add(b.getName());
                }
                if (b.getSysUserId() != null) {
                    s.bound++;
                }
            }
            if (created) {
                bindingMapper.insert(b);
            } else {
                b.setUpdatedBy("dirsync");
                bindingMapper.updateById(b);
            }
        }
        if (truncated) {
            s.reason = "达到护栏上限（部门≤" + MAX_DEPTS + "/用户≤" + MAX_USERS + "/深度≤" + MAX_DEPTH + "），结果可能不完整";
        }
        s.ok = true;
        audit("SUCCESS", truncated ? s.reason : null, "depts=" + s.depts + " users=" + s.users
                + " bound=" + s.bound + " manualKept=" + s.manualKept);
        return s;
    }

    /** 按钉钉 userid 查既有绑定（protected 供测试覆盖注入 MANUAL 场景） */
    protected SysDingtalkBinding findExisting(String userid) {
        return bindingMapper.selectOne(new LambdaQueryWrapper<SysDingtalkBinding>()
                .eq(SysDingtalkBinding::getDingtalkUserid, userid)
                .last("LIMIT 1"));
    }

    /** 匹配规则（A-44）：MOBILE（phone 精确）→ NAME（realName 唯一）→ NONE */
    void match(SysDingtalkBinding b, JsonNode u, List<SysUser> sysUsers) {
        String mobile = textOrNull(u.path("mobile"));
        if (mobile != null) {
            for (SysUser su : sysUsers) {
                if (mobile.equals(su.getPhone())) {
                    b.setSysUserId(su.getId());
                    b.setMatchType("MOBILE");
                    return;
                }
            }
        }
        String name = textOrNull(u.path("name"));
        if (name != null) {
            SysUser hit = null;
            int hits = 0;
            for (SysUser su : sysUsers) {
                if (name.equals(su.getRealName())) {
                    hit = su;
                    hits++;
                }
            }
            if (hits == 1) {
                b.setSysUserId(hit.getId());
                b.setMatchType("NAME");
                return;
            }
        }
        b.setSysUserId(null);
        b.setMatchType("NONE");
    }

    private static String textOrNull(JsonNode node) {
        String v = node.asText("");
        return v.isEmpty() ? null : v;
    }

    private void audit(String status, String errorMessage, String payloadSummary) {
        if (auditRecorder != null) {
            auditRecorder.record("DINGTALK", "PULL", "dirsync", status, errorMessage, payloadSummary);
        }
    }
}
