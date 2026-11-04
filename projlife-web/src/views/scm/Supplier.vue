<template>
  <!-- SPRINT-UID-R20：供应商准入对齐真实契约——列表接 GET /scm/suppliers（反向断链修复）、
       准入详情接 GET /suppliers/{id}/onboarding（此前本地假 stage）、注册语义纠正
       （md_supplier 建档在主数据工作台 /mdm/suppliers-register，此处建准入档案） -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-input v-model="query.keyword" size="small" placeholder="供应商名称" style="width: 180px" clearable @keyup.enter.native="load" />
        <el-select v-model="query.status" size="small" style="width: 130px" clearable placeholder="状态" @change="load">
          <el-option value="PENDING" label="待准入" /><el-option value="QUALIFIED" label="合格供方" />
        </el-select>
        <el-button size="small" icon="el-icon-search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button v-permission="'mdm:supplier:manage'" size="small" icon="el-icon-office-building" @click="$router.push('/mdm/project')">去主数据建档</el-button>
        <el-button v-permission="'scm:supplier:manage'" size="small" type="primary" icon="el-icon-plus" @click="registerDialog = true">注册准入档案</el-button>
      </div>
      <el-table :data="suppliers" v-loading="loading" size="small">
        <el-table-column prop="supplierCode" label="编码" width="110" />
        <el-table-column prop="supplierName" label="供应商名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="creditCode" label="统一社会信用代码" width="180" />
        <el-table-column prop="status" label="档案状态" width="100">
          <template slot-scope="{ row }">
            <pt-status-tag :value="row.status || 'PENDING'" :dict="'PENDING:待准入,QUALIFIED:合格供方,FROZEN:冻结'" />
          </template>
        </el-table-column>
        <el-table-column prop="blacklistFlag" label="黑名单" width="80">
          <template slot-scope="{ row }">
            <el-tag v-if="row.blacklistFlag === 1" size="mini" type="danger">在列</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template slot-scope="{ row }">
            <el-button v-permission="'scm:supplier:manage'" size="mini" type="text" @click="openRegisterOnboarding(row)">注册准入</el-button>
            <el-button v-permission="'scm:supplier:manage'" size="mini" type="text" @click="viewOnboarding(row)">准入详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="suppliers.length === 0 && !loading" description="暂无供应商（先在主数据工作台建档，再注册五阶准入）" :image-size="50" />
    </el-card>

    <!-- 注册准入档案弹窗（FR-SCM-01：对已建档 md_supplier 建准入档案） -->
    <el-dialog title="注册准入档案（五阶准入第 1 阶）" :visible.sync="registerDialog" width="440px">
      <el-form label-width="130px" size="small">
        <el-form-item label="供应商ID" required><el-input-number v-model="registerForm.supplierId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
      </el-form>
      <p class="muted" style="margin: 0">准入档案挂在 md_supplier 之上；新供应商请先在主数据工作台「供应商注册」建档。</p>
      <div slot="footer">
        <el-button size="small" @click="registerDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="register">注册</el-button>
      </div>
    </el-dialog>

    <!-- 阶段推进弹窗（passed=false → REJECTED 终态；APPROVED 自动置 md_supplier 合格） -->
    <el-dialog title="准入阶段推进" :visible.sync="advDialog" width="440px">
      <div v-if="advTarget" class="muted" style="margin-bottom: 8px">当前阶段：{{ stageLabel(advTarget.currentStage) }}</div>
      <el-form label-width="90px" size="small">
        <el-form-item label="审核结论" required>
          <el-radio-group v-model="advForm.passed">
            <el-radio :label="true">通过，进入下一阶</el-radio>
            <el-radio :label="false">不通过（终态拒绝）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见"><el-input v-model="advForm.auditResult" type="textarea" :rows="2" placeholder="资质核验/送检结论等" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="advDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doAdvance">提交</el-button>
      </div>
    </el-dialog>

    <!-- 五阶准入详情抽屉 -->
    <el-drawer :visible.sync="onboardingDrawer" size="45%" title="五阶准入详情">
      <div style="padding: 0 16px">
        <template v-if="onboarding">
          <el-descriptions :column="1" size="small" border style="margin-bottom: 16px">
            <el-descriptions-item label="当前阶段">{{ stageLabel(onboarding.currentStage) }}</el-descriptions-item>
            <el-descriptions-item label="阶段状态">
              <pt-status-tag :value="onboarding.stageStatus || 'PENDING'" :dict="'PASSED:已通过,FAILED:未通过,PENDING:进行中'" />
            </el-descriptions-item>
            <el-descriptions-item label="审核意见">{{ onboarding.auditResult || '—' }}</el-descriptions-item>
            <el-descriptions-item label="最终评定">{{ onboarding.finalLevel || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-button v-if="onboarding.currentStage !== 'APPROVED' && onboarding.currentStage !== 'REJECTED'"
                     v-permission="'scm:supplier:manage'" size="small" type="primary" plain @click="openAdvance(onboarding)">推进下一阶</el-button>
        </template>
        <el-empty v-else description="该供应商尚无准入档案（先注册准入）" :image-size="60" />
        <el-steps :active="stageIndex" direction="vertical" style="margin: 16px 0">
          <el-step v-for="s in stages" :key="s.code" :title="s.label" :description="s.desc"
                   :status="stageIndex > s.step ? 'finish' : stageIndex === s.step ? 'process' : 'wait'" />
        </el-steps>
      </div>
    </el-drawer>
  </div>
</template>
<script>
import { PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

/** 与后端 ScmService.STAGES 一致（REJECTED 为拒绝终态） */
const STAGES = [
  { code: 'REGISTERED', step: 1, label: '注册', desc: '基础信息登记' },
  { code: 'QUALIFIED', step: 2, label: '资质审核', desc: '三证/体系认证核验' },
  { code: 'VISITED', step: 3, label: '现场考察', desc: '实地评审' },
  { code: 'SAMPLED', step: 4, label: '样品送检', desc: '首批样品 IQC 检验' },
  { code: 'APPROVED', step: 5, label: '合格供方', desc: '准入完成（自动置合格）' }
]
const STAGE_MAP = {}
STAGES.forEach(s => { STAGE_MAP[s.code] = s })

export default {
  name: 'Supplier',
  components: { PtStatusTag },
  data() {
    return {
      stages: STAGES,
      query: { keyword: '', status: '' },
      suppliers: [], loading: false, total: 0,
      page: { pageNum: 1, pageSize: 20 },
      registerDialog: false, registerForm: { supplierId: null },
      advDialog: false, advTarget: null, advForm: { passed: true, auditResult: '' },
      onboardingDrawer: false, onboarding: null, stageIndex: 0
    }
  },
  mounted() { this.load() },
  methods: {
    stageLabel(code) { return STAGE_MAP[code] ? STAGE_MAP[code].label : (code || '—') },
    load() {
      this.loading = true
      const params = { pageNum: this.page.pageNum, pageSize: this.page.pageSize }
      if (this.query.keyword) params.keyword = this.query.keyword
      if (this.query.status) params.status = this.query.status
      this.$http.get('/scm/suppliers', { params }).then(body => {
        const pageData = body.data || {}
        this.suppliers = pageData.records || []
        this.total = pageData.total || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    register() {
      if (!this.registerForm.supplierId) { this.$message.warning('供应商ID必填'); return }
      this.$http.post(`/scm/suppliers/${this.registerForm.supplierId}/register`).then(() => {
        this.$message.success('准入档案已注册（REGISTERED，可开始推进）')
        this.registerDialog = false
        this.load()
      }).catch(showError)
    },
    openRegisterOnboarding(row) {
      this.registerForm = { supplierId: row.id }
      this.registerDialog = true
    },
    viewOnboarding(row) {
      this.onboardingDrawer = true
      this.$http.get(`/scm/suppliers/${row.id}/onboarding`).then(body => {
        this.onboarding = body.data
        this.stageIndex = this.onboarding ? (STAGE_MAP[this.onboarding.currentStage] ? STAGE_MAP[this.onboarding.currentStage].step : 0) : 0
      }).catch(() => { this.onboarding = null; this.stageIndex = 0 })
    },
    openAdvance(onboarding) {
      this.advTarget = onboarding
      this.advForm = { passed: true, auditResult: '' }
      this.advDialog = true
    },
    doAdvance() {
      this.$http.post(`/scm/suppliers/${this.advTarget.supplierId}/advance`, this.advForm).then(body => {
        const next = body.data && body.data.currentStage
        this.$message.success(next === 'REJECTED' ? '已标记不通过（REJECTED 终态）' : '推进至：' + this.stageLabel(next))
        this.advDialog = false
        this.load()
        if (this.onboardingDrawer) this.viewOnboarding({ id: this.advTarget.supplierId })
      }).catch(showError)
    }
  }
}
</script>
<style scoped>
.muted { color: var(--ink-3); font-size: 12px; }
.spacer { flex: 1; }
</style>
