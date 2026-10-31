<template>
  <!-- P4 模板占位（UID-03 §2.1 P4 + UID-05 模板档）：按页面族骨架生成，字段随切片细化；
       空态带主行动（FR-SYS-24），禁"暂无数据"裸文案 -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <span class="pl-stub-title">{{ title }}</span>
        <span class="muted">P4 模板骨架 · 随切片按 UID-03 §2.1 细化字段与列</span>
        <div class="spacer"></div>
        <el-button size="small" type="primary" :disabled="true">新建</el-button>
      </div>
      <div class="pt-empty" style="padding: 48px 0">
        <i class="el-icon-document" aria-hidden="true"></i>
        <div class="pt-empty-title">该页属于 UID-05 模板档</div>
        <div class="pt-empty-hint">路由与菜单已登记（验收 1：无孤儿页面）；功能随模块切片交付</div>
        <el-button size="small" @click="$router.push('/workbench')">返回工作台</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'TemplateStub',
  computed: {
    title() { return (this.$route.meta && this.$route.meta.title) || '模块页' }
  }
}
</script>

<style scoped>
.pl-stub-title { font-weight: 600; margin-right: 12px; }
.muted { color: var(--ink-3); font-size: 12px; }
.pt-empty { text-align: center; }
.pt-empty .el-icon-document { font-size: 48px; color: var(--ink-4); }
.pt-empty-title { font-size: 16px; font-weight: 600; color: var(--ink); margin: 8px 0 4px; }
.pt-empty-hint { font-size: 12px; color: var(--ink-3); margin-bottom: 12px; }
</style>
