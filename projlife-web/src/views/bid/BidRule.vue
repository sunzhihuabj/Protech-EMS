<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <span style="font-weight:600">标讯订阅规则（FR-BID-09/10：按关键词/区域/金额区间/类目匹配推送）</span>
        <span class="spacer"></span>
        <el-button size="small" type="primary" v-permission="'bid:push:subscribe'"
                   @click="save">{{ form.id ? '保存修改' : '创建订阅' }}</el-button>
      </div>
      <el-form label-width="110px" size="small" class="br-form">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="正向关键词">
              <el-input v-model="form.keywords" placeholder="逗号分隔，如：风电,消防,技改" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="区域白名单">
              <el-input v-model="form.regions" placeholder="逗号分隔，如：华东,华北" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="类目">
              <el-input v-model="form.categories" placeholder="逗号分隔，如：EPC,服务" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :lg="6">
            <el-form-item label="金额区间(万)">
              <el-col :span="11"><el-input-number v-model="form.amountMin" :min="0" :controls="false" style="width:100%" placeholder="下限" /></el-col>
              <el-col :span="2" class="br-dash">—</el-col>
              <el-col :span="11"><el-input-number v-model="form.amountMax" :min="0" :controls="false" style="width:100%" placeholder="上限" /></el-col>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-alert type="info" :closable="false" show-icon
                title="订阅规则为全局匹配口径：每用户一条，保存即覆盖；命中后走 /bid/notices 推送与消息中心提醒（FR-BID-10）" />
    </el-card>
  </div>
</template>
<script>
import { showError } from '@/api/request'

/**
 * SPRINT-UID-R15：修复空壳页——原表格恒为空（无任何 API 调用），
 * 改为"我的订阅规则"编辑页（GET/POST /bid/subscribe）。
 */
export default {
  name: 'BidRule',
  data() {
    return {
      form: { id: null, keywords: '', regions: '', categories: '', owners: '', amountMin: null, amountMax: null },
      loading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/bid/subscribe').then(body => {
        if (body.data) { this.form = { ...this.form, ...body.data } }
      }).catch(showError).finally(() => { this.loading = false })
    },
    save() {
      this.$http.post('/bid/subscribe', this.form).then(() => {
        this.$message.success('订阅规则已保存')
        this.load()
      }).catch(showError)
    }
  }
}
</script>
<style scoped>
.br-form { margin-bottom: 12px; }
.br-dash { text-align: center; color: var(--ink-3); }
</style>
