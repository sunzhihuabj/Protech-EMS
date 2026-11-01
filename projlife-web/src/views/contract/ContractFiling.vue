<template>
  <div class="pl-page">
    <!-- 头部 -->
    <div class="pl-doc-head">
      <span class="pl-doc-no">{{ c.contractNo || '（未用印）' }}</span>
      <span class="pl-doc-name">{{ c.contractName }}</span>
      <span class="spacer"></span>
      <div class="pl-completeness">
        <el-progress :percentage="completenessPct" :stroke-width="8" style="width:180px" />
        <span class="pl-comp-text">{{ haveCount }}/26 · 缺 {{ 26 - haveCount }} 项</span>
      </div>
    </div>

    <!-- 26 要素表单 -->
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <pt-form-section title="基本要素（缺失项点击跳转）" :error-count="missingCount">
        <el-form label-width="120px" size="small">
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="合同名称" required><el-input v-model.trim="c.contractName" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="合同性质" required><el-input v-model.trim="c.nature" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="甲方" required><el-input v-model.trim="c.partyA" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="乙方" required><el-input v-model.trim="c.partyB" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="金额" required><el-input v-model.number="c.amount" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="税率" required><el-input v-model.number="c.taxRate" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="付款条件" required><el-input v-model.trim="c.paymentTerms" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="签订日" required><el-date-picker v-model="c.signDate" type="date" value-format="yyyy-MM-dd" style="width:100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="生效日" required><el-date-picker v-model="c.effectiveDate" type="date" value-format="yyyy-MM-dd" style="width:100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="交货地址"><el-input v-model.trim="c.deliveryAddr" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="质保期(月)"><el-input v-model.number="c.warrantyMonths" /></el-form-item></el-col>
            <el-form-item label="联系人" required><el-input v-model.trim="c.contact" /></el-form-item>
          </el-row>
        </el-form>
      </pt-form-section>
      <div style="margin-top:16px;display:flex;gap:8px">
        <el-button size="small" type="primary" :disabled="haveCount < 18"
                   @click="submitFiling">提交备案（≥18/26 可提交）</el-button>
        <span v-if="haveCount < 18" style="font-size:12px;color:var(--danger-ink);line-height:32px">
          还缺 {{ 18 - haveCount }} 项，不可提交（FR-CT-21）
        </span>
      </div>
    </el-card>
  </div>
</template>

<script>
import { PtFormSection } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'ContractFiling',
  components: { PtFormSection },
  data() {
    return { c: {} }
  },
  computed: {
    id() { return this.$route.params.id },
    filled() {
      const keys = ['contractName','nature','partyA','partyB','amount','taxRate','paymentTerms',
        'signDate','effectiveDate','deliveryAddr','contact','phone','warrantyMonths',
        'currency','invoiceType','bankName','bankAccount','taxNo','companySeal','legalRepresentative',
        'authorizeLetter','insuranceCert','qualityCert','safetyCert','envCert','otherAttachments']
      let count = 0
      const self = this
      keys.forEach(k => { if (self.c[k]) count++ })
      return count
    },
    haveCount() { return this.filled },
    missingCount() { return 26 - this.haveCount },
    completenessPct() { return Math.round(this.haveCount / 26 * 100) }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.$http.get(`/contract/${this.id}`).then(body => { this.c = body.data }).catch(showError)
    },
    submitFiling() {
      this.$http.post(`/contract/${this.id}/filing`, this.c).then(() => {
        this.$message.success('备案已提交')
        this.$router.push('/ct/contract')
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.pl-doc-head {
  height: 56px; display: flex; align-items: center; gap: 12px;
  background: var(--surface); border: 1px solid var(--line-2); border-radius: var(--r-3, 6px);
  padding: 0 20px; margin-bottom: 8px;
}
.pl-doc-no { font-family: var(--mono); font-size: 14px; color: var(--ink-2); }
.pl-doc-name { font-size: var(--fs-16, 16px); font-weight: 600; color: var(--ink); }
.pl-completeness { display: flex; align-items: center; gap: 8px; }
.pl-comp-text { font-size: 12px; color: var(--ink-2); font-variant-numeric: tabular-nums; }
.spacer { flex: 1; }
</style>
