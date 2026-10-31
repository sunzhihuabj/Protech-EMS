<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-button v-permission="'scm:supplier:manage'" size="small" type="primary" icon="el-icon-plus" @click="registerDialog = true">注册供应商</el-button>
      </div>
      <el-table :data="suppliers" v-loading="loading" size="small">
        <el-table-column prop="supplierName" label="供应商名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="creditCode" label="统一社会信用代码" width="180" />
        <el-table-column prop="stage" label="准入阶段" width="130">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.stage >= 5 ? 'success' : row.stage >= 3 ? '' : 'info'">{{ stageLabel(row.stage) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" width="180">
          <template slot-scope="{ row }">
            <el-button v-if="row.stage < 5" v-permission="'scm:supplier:manage'" size="mini" type="text" @click="advance(row)">推进准入</el-button>
            <el-button size="mini" type="text" @click="viewOnboarding(row)">准入详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="suppliers.length === 0 && !loading" description="暂无供应商（FR-SCM-01~04 五阶准入）" :image-size="50" />
    </el-card>
    <el-dialog title="注册供应商（五阶准入第 1 阶）" :visible.sync="registerDialog" width="460px">
      <el-form label-width="140px" size="small">
        <el-form-item label="供应商名称" required><el-input v-model.trim="form.supplierName" /></el-form-item>
        <el-form-item label="统一社会信用代码" required><el-input v-model.trim="form.creditCode" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="registerDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="register">注册</el-button>
      </div>
    </el-dialog>
    <el-drawer :visible.sync="onboardingDrawer" size="45%" title="五阶准入详情">
      <div style="padding: 0 16px">
        <el-steps :active="onboardingStage" direction="vertical" style="margin: 16px 0">
          <el-step v-for="s in stages" :key="s.step" :title="s.label" :description="s.desc"
                   :status="onboardingStage > s.step ? 'finish' : onboardingStage === s.step ? 'process' : 'wait'" />
        </el-steps>
      </div>
    </el-drawer>
  </div>
</template>
<script>
import { showError } from '@/api/request'
const STAGES = [
  { step: 1, label: '注册', desc: '基础信息登记' },
  { step: 2, label: '资质审核', desc: '三证/体系认证核验' },
  { step: 3, label: '样品送检', desc: '首批样品 IQC 检验' },
  { step: 4, label: '小批试用', desc: '小批量交付验证' },
  { step: 5, label: '合格供方', desc: '准入完成' }
]
export default {
  name: 'Supplier',
  data() { return { suppliers: [], loading: false, registerDialog: false, form: {}, onboardingDrawer: false, onboardingStage: 1, stages: STAGES } },
  mounted() { this.load() },
  methods: {
    stageLabel(s) { return STAGES[s - 1] ? STAGES[s - 1].label : '阶段 ' + s },
    load() {
      this.loading = true
      this.$http.get('/scm/suppliers').then(body => { this.suppliers = body.data.records || body.data || [] }).catch(showError).finally(() => { this.loading = false })
    },
    register() {
      this.$http.post('/scm/suppliers/0/register', this.form).then(() => { this.$message.success('已注册'); this.registerDialog = false; this.load() }).catch(showError)
    },
    advance(row) {
      this.$http.post(`/scm/suppliers/${row.id}/advance`).then(body => { this.$message.success('推进至：' + this.stageLabel(body.data.stage || row.stage + 1)); this.load() }).catch(showError)
    },
    viewOnboarding(row) { this.onboardingStage = row.stage || 1; this.onboardingDrawer = true }
  }
}
</script>
