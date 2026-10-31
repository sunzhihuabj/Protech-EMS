<template>
  <!-- 403 三型（UID-05 §二⑨ / UID-02 §8.3 / UID-03 §三 第 4 行）：
       type=endpoint（BR-08 整页端限制）/ permission（2002 功能级）/ scope（2003 范围外） -->
  <div class="pl-403">
    <div class="pl-403-card">
      <div class="pl-403-icon" aria-hidden="true"><i :class="icon"></i></div>
      <h2>{{ title }}</h2>
      <p class="pl-403-desc">{{ desc }}</p>

      <!-- 端限制（BR-08）：小程序码占位 + 联系文案；禁止显示"账号或密码错误" -->
      <div v-if="type === 'endpoint'" class="pl-403-qr" aria-label="微信小程序码">
        <i class="el-icon-mobile-phone" style="font-size: 48px; color: var(--ink-4)"></i>
        <p class="muted">小程序码（部署批次生成）</p>
      </div>

      <!-- 功能级无权限：逐条列出 missingPerm + 复制 + 申请权限（走钉钉审批 B-04） -->
      <div v-if="type === 'permission' && missingPerms.length" class="pl-403-perms">
        <p class="muted">缺少权限：</p>
        <code v-for="p in missingPerms" :key="p" class="pl-403-code">{{ p }}</code>
        <div style="margin-top: 8px">
          <el-button size="mini" @click="copyPerms">复制权限码</el-button>
          <el-button size="mini" type="primary" plain @click="applyPerm">申请权限</el-button>
        </div>
      </div>

      <div class="pl-403-actions">
        <el-button v-if="type !== 'endpoint'" size="small" @click="$router.back()">返回上一页</el-button>
        <el-button v-if="type === 'endpoint'" size="small" @click="logout">退出登录</el-button>
        <el-button v-if="type === 'scope'" size="small" type="primary" @click="$router.push('/workbench')">返回工作台</el-button>
      </div>
      <p class="muted" style="margin-top: 16px">内部系统，仅限授权人员使用；所有操作留痕审计。</p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ForbiddenPage',
  computed: {
    type() {
      return this.$route.query.type || 'permission'
    },
    missingPerms() {
      const raw = this.$route.query.perms
      return raw ? String(raw).split(',') : []
    },
    icon() {
      return { endpoint: 'el-icon-mobile-phone', permission: 'el-icon-lock', scope: 'el-icon-view' }[this.type] || 'el-icon-lock'
    },
    title() {
      return {
        endpoint: '本账号仅支持移动端使用',
        permission: '当前账号无此权限',
        scope: '该数据不在你的数据范围内'
      }[this.type] || '无权访问'
    },
    desc() {
      return {
        endpoint: '施工人员请使用微信小程序完成图纸查看、日志填报与整改反馈；如需 Web 端权限请联系企管部开通（BR-08）',
        permission: '权限由企管部统一分配，可通过审批流程申请开通',
        scope: '该数据不在你的数据范围（当前角色范围）内；范围由角色决定，可切换数据范围或联系管理员'
      }[this.type] || ''
    }
  },
  methods: {
    copyPerms() {
      const text = this.missingPerms.join(',')
      if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => this.$message.success('已复制'))
      } else {
        this.$message.info(text)
      }
    },
    applyPerm() {
      // 钉钉审批（B-04）通道；未接入前站内提示
      this.$message.info('权限申请已提交至企管部审批（钉钉通道接入后自动流转）')
    },
    logout() {
      this.$store.dispatch('logout')
    }
  }
}
</script>

<style scoped>
.pl-403 {
  height: 100%; display: flex; align-items: center; justify-content: center; background: var(--bg);
}
.pl-403-card {
  width: 480px; padding: 32px; text-align: center;
  background: var(--surface); border: 1px solid var(--line-2); border-radius: var(--r-4);
  box-shadow: var(--elev-2);
}
.pl-403-icon { font-size: 40px; color: var(--warn-ink); margin-bottom: 12px; }
.pl-403-card h2 { margin: 0 0 8px; font-size: 20px; color: var(--ink); }
.pl-403-desc { color: var(--ink-2); font-size: 14px; line-height: 22px; }
.pl-403-qr { margin: 16px 0; }
.pl-403-perms { margin: 12px 0; text-align: left; }
.pl-403-code {
  display: inline-block; margin: 2px 4px; padding: 2px 8px;
  font-family: var(--mono); font-size: 12px; color: var(--ink-2);
  background: var(--surface-3); border-radius: var(--r-1);
}
.pl-403-actions { margin-top: 16px; }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
