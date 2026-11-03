<template>
  <!-- P5 单据详情七区（UID-03 §2.3，顺序固定禁止调换）：
       1 头部状态条 56 → 2 操作区 48 → 3 主体信息（左 16 列）→ 4 关联单据（右 8 列）
       → 5 附件（BR-07 只读水印件）→ 6 流转时间轴 → 7 审批记录（暂以操作留痕承载） -->
  <div class="pl-page">
    <!-- 1 头部状态条 -->
    <div class="pl-doc-head">
      <span class="pl-doc-no" aria-label="合同编号">{{ c.contractNo || '（未用印）' }}</span>
      <span class="pl-doc-name">{{ c.contractName }}</span>
      <pt-status-tag :value="c.status || 'DRAFT'" :dict="statusDict" />
      <span class="pl-doc-meta">版本 V1.0</span>
      <span class="spacer"></span>
      <span class="pl-doc-meta">更新 {{ c.updatedAt || '—' }}</span>
    </div>

    <!-- 2 操作区（主操作 1 + 次级 ≤4；状态导致只读按 §2.3(b)） -->
    <div class="pl-doc-actions">
      <template v-if="editable">
        <el-button v-permission="'contract:review:submit'" size="small" type="primary" @click="submitReview">提交评审</el-button>
      </template>
      <template v-else>
        <span class="pl-readonly-tip">{{ readonlyTip }}</span>
      </template>
      <el-button v-permission="'contract:seal'" size="small" :disabled="c.status !== 'REVIEWED'" @click="seal">用印登记</el-button>
      <el-button v-permission="'contract:confirm'" size="small" :disabled="c.status !== 'SIGNED'" @click="goFiling">建档备案</el-button>
      <el-button size="small" @click="aiRisk">AI 风险</el-button>
      <div class="spacer"></div>
      <el-button size="small" icon="el-icon-back" @click="$router.push('/ct/contract')">返回台账</el-button>
    </div>
    <!-- BR-02 冻结整页态（§2.3(b)：顶部条 + 去补建档） -->
    <pt-block-alert v-if="c.invoiceFrozen" block-code="BR-02"
                    reason="开票已冻结：第 4 个工作日未完成建档" link="/ct/contract" link-text="查看冻结清单" />

    <el-row :gutter="16">
      <!-- 3 主体信息（左 16 列，语义分组） -->
      <el-col :span="16">
        <el-card shadow="never">
          <pt-form-section title="基本信息">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="合同性质">{{ c.nature }}</el-descriptions-item>
              <el-descriptions-item label="业务类型">{{ c.businessType || '—' }}</el-descriptions-item>
              <el-descriptions-item label="甲方">{{ c.partyA || '—' }}</el-descriptions-item>
              <el-descriptions-item label="乙方">{{ c.partyB || '—' }}</el-descriptions-item>
              <el-descriptions-item label="法人主体">{{ c.legalEntityId || '—' }}</el-descriptions-item>
              <el-descriptions-item label="相对方">{{ c.counterpartyId || '—' }}</el-descriptions-item>
            </el-descriptions>
          </pt-form-section>
          <pt-form-section title="金额与付款">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="金额(元)">
                <!-- BR-06/A-07：服务端 null → ***（--ink-2）；无值 → — -->
                <span v-if="c.amount === null || c.amount === undefined" class="pl-empty-val" aria-label="无数据">—</span>
                <span v-else class="pl-num">{{ Number(c.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="税率">{{ c.taxRate || '—' }}</el-descriptions-item>
              <el-descriptions-item label="付款条件">{{ c.paymentTerms || '—' }}</el-descriptions-item>
              <el-descriptions-item label="币种">{{ c.currency || 'CNY' }}</el-descriptions-item>
            </el-descriptions>
          </pt-form-section>
          <pt-form-section title="交期">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="签订日">{{ c.signDate || '—' }}</el-descriptions-item>
              <el-descriptions-item label="生效日">{{ c.effectiveDate || '—' }}</el-descriptions-item>
              <el-descriptions-item label="交付地址">{{ c.deliveryAddr || '—' }}</el-descriptions-item>
              <el-descriptions-item label="建档截止">{{ c.filingDeadline || '—' }}</el-descriptions-item>
            </el-descriptions>
          </pt-form-section>
        </el-card>
      </el-col>

      <!-- 4 关联单据 + 5 附件（右 8 列；1366 下移到主体下方全宽由栅格自动承载） -->
      <el-col :span="8">
        <el-card shadow="never" style="margin-bottom: 16px">
          <div slot="header"><b>关联单据</b></div>
          <div class="rel-group"><b class="rel-title">关联项目（{{ related.projects.length }}）</b>
            <div v-if="!related.projects.length" class="muted">未创建</div>
            <div v-for="p in related.projects" :key="'p'+p.id" class="rel-row">
              <el-link type="primary" @click="$router.push({ name: 'prjProjectDetail', params: { id: String(p.id) } })">{{ p.projectNo }}</el-link>
              <span>{{ p.projectName }}</span><span class="muted">{{ p.status }} · {{ p.progressPct == null ? '—' : p.progressPct + '%' }}</span>
            </div>
          </div>
          <div class="rel-group"><b class="rel-title">开票（{{ related.invoices.length }}）</b>
            <div v-if="!related.invoices.length" class="muted">未创建</div>
            <div v-for="i in related.invoices" :key="'i'+i.id" class="rel-row">
              <span>{{ i.invoiceNo }}</span><span>{{ i.invoiceType }}</span>
              <span class="muted">¥{{ i.amount }} · {{ i.status }}</span>
            </div>
          </div>
          <div class="rel-group"><b class="rel-title">收款计划（{{ related.paymentPlans.length }}）</b>
            <div v-if="!related.paymentPlans.length" class="muted">未创建</div>
            <div v-for="pl in related.paymentPlans" :key="'pl'+pl.id" class="rel-row">
              <span>{{ pl.phaseName }}</span><span>{{ pl.percentage }}%</span>
              <span class="muted">¥{{ pl.amount }} · 计划 {{ pl.planDate }} · {{ pl.status }}</span>
            </div>
          </div>
        </el-card>
        <!-- SPRINT-UID-R15：财务与交底动作（开票/回款/交底/解冻——修复合同财务闭环无入口断链） -->
        <el-card shadow="never" style="margin-bottom: 16px">
          <div slot="header"><b>财务与交底</b></div>
          <div class="fin-actions">
            <el-button v-permission="'contract:invoice:apply'" size="small" type="primary" plain @click="invDialog = true">登记开票</el-button>
            <el-button v-permission="'contract:payment:create'" size="small" type="primary" plain @click="rcDialog = true">登记回款</el-button>
            <el-button v-permission="'contract:dispatch'" size="small" type="warning" plain @click="dpDialog = true">发起交底</el-button>
            <el-button v-if="c.status === 'FROZEN'" v-permission="'contract:unfreeze'" size="small" type="success" plain @click="unfreeze">解除冻结</el-button>
          </div>
          <div class="rel-group" style="margin-top:10px">
            <b class="rel-title">开票确认</b>
            <div v-if="!related.invoices.some(i => i.status === 'PENDING')" class="muted">无待确认开票</div>
            <div v-for="i in related.invoices.filter(x => x.status === 'PENDING')" :key="'cfm'+i.id" class="rel-row">
              <span>{{ i.invoiceNo }}</span><span class="muted">¥{{ i.amount }}</span>
              <el-button v-permission="'contract:invoice:confirm'" size="mini" type="text" class="fin-act" @click="confirmInvoice(i)">确认开票</el-button>
            </div>
          </div>
        </el-card>
        <el-card shadow="never">
          <div slot="header"><b>附件（只读水印件，BR-07）</b></div>
          <p class="muted">暂无附件（上传入口随 file-service 批次）</p>
        </el-card>
      </el-col>
    </el-row>

    <!-- SPRINT-UID-R15：登记开票弹窗 -->
    <el-dialog title="登记开票（FR-CT-14）" :visible.sync="invDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="发票类型" required>
          <el-select v-model="invForm.invoiceType" style="width: 100%">
            <el-option value="VAT_SPECIAL" label="增值税专用发票" /><el-option value="VAT_NORMAL" label="增值税普通发票" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额(元)" required><el-input-number v-model="invForm.amount" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="税额(元)"><el-input-number v-model="invForm.taxAmount" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="开票日期" required><el-date-picker v-model="invForm.invoiceDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="invDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createInvoice">提交</el-button>
      </div>
    </el-dialog>

    <!-- SPRINT-UID-R15：登记回款弹窗 -->
    <el-dialog title="登记回款（FR-CT-15）" :visible.sync="rcDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="回款金额(元)" required><el-input-number v-model="rcForm.amount" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="回款日期" required><el-date-picker v-model="rcForm.receiptDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="方式">
          <el-select v-model="rcForm.method" style="width: 100%">
            <el-option value="BANK" label="银行转账" /><el-option value="BILL" label="票据" /><el-option value="OTHER" label="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="银行流水号"><el-input v-model.trim="rcForm.bankRef" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="rcDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createReceipt(true)">登记并确认</el-button>
        <el-button size="small" @click="createReceipt(false)">仅登记</el-button>
      </div>
    </el-dialog>

    <!-- SPRINT-UID-R15：发起交底弹窗 -->
    <el-dialog title="发起交底（FR-CT-10，交底及时率 KPI 数据源）" :visible.sync="dpDialog" width="460px">
      <el-form label-width="110px" size="small">
        <el-form-item label="接收人ID列表" required>
          <el-input v-model.trim="dpForm.receiverIds" placeholder="逗号分隔，如：5,8,12" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dpDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createDispatch">发送交底</el-button>
      </div>
    </el-dialog>

    <!-- 6 流转时间轴（操作留痕承载；逆序） -->
    <el-card shadow="never" style="margin-top: 16px">
      <div slot="header"><b>流转时间轴</b></div>
      <el-empty v-if="!logs.length" description="暂无流转记录" :image-size="50" />
      <el-timeline>
        <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="l.createdAt">
          {{ l.action }} · {{ l.operateBy || '' }}
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 7 审批记录（SPRINT-PRJ2：R8 审批流真实实例 + 节点时间线） -->
    <el-card shadow="never" style="margin-top: 16px">
      <div slot="header"><b>审批记录（CONTRACT_REVIEW{{ approval.approvalNo ? ' · ' + approval.approvalNo : '' }}）</b></div>
      <el-empty v-if="!approval.approvalNo" description="未走审批流（projlife.approval.enabled=false 直达，或未提交评审）" :image-size="50" />
      <el-timeline v-else>
        <el-timeline-item v-for="n in approvalNodes" :key="n.nodeSeq"
                          :type="n.status === 'APPROVED' ? 'success' : (n.status === 'REJECTED' ? 'danger' : (n.status === 'PENDING' ? 'primary' : 'info'))"
                          :timestamp="n.actedAt || ''">
          <b>#{{ n.nodeSeq }} {{ n.nodeName }}</b> ·
          <pt-status-tag :value="n.status" :dict="'PENDING:待审批,APPROVED:已通过,REJECTED:已驳回,SKIPPED:已跳过'" />
          <span v-if="n.comment" class="muted">意见：{{ n.comment }}</span>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script>
import { PtStatusTag, PtFormSection, PtBlockAlert } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'ContractDetail',
  components: { PtStatusTag, PtFormSection, PtBlockAlert },
  data() {
    return {
      c: {},
      related: { projects: [], invoices: [], paymentPlans: [] },
      approval: {},
      approvalNodes: [],
      logs: [],
      statusDict: 'DRAFT:编辑中,REVIEWED:已评审,SIGNED:已签署,PERFORMING:履行中,ACCEPTED:已验收,WARRANTY:质保期中,CLOSED:已关闭,CHANGED:已变更',
      // SPRINT-UID-R15：财务与交底动作
      invDialog: false, invForm: { invoiceType: 'VAT_SPECIAL' },
      rcDialog: false, rcForm: { method: 'BANK' },
      dpDialog: false, dpForm: {}
    }
  },
  computed: {
    id() { return this.$route.params.id },
    editable() { return this.c.status === 'DRAFT' },
    readonlyTip() {
      if (this.c.status === 'FROZEN' || this.c.invoiceFrozen) return '开票已冻结，主操作「去补建档」'
      if (['REVIEWING', 'REVIEWED'].indexOf(this.c.status) !== -1) return '审批/评审中，不可修改'
      if (['SIGNED', 'FILED', 'PERFORMING', 'CLOSED', 'CHANGED'].indexOf(this.c.status) !== -1) return '已生效单据，修改请走变更单'
      return ''
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.$http.get(`/contract/${this.id}`).then(body => { this.c = body.data }).catch(showError)
      // SPRINT-PRJ2：R8 审批流真实实例（CONTRACT_REVIEW by target）+ 节点
      this.$http.get(`/approval/target/CONTRACT_REVIEW/${this.id}`).then(body => {
        this.approval = body.data || {}
        if (this.approval.id) {
          this.$http.get(`/approval/${this.approval.id}/nodes`).then(nb => {
            this.approvalNodes = nb.data || []
          }).catch(() => {})
        } else {
          this.approvalNodes = []
        }
      }).catch(() => {})
      // SPRINT-PRJ1（F9）：关联单据链（项目/开票/收款计划）
      this.$http.get(`/contract/${this.id}/related`).then(body => {
        this.related = body.data || { projects: [], invoices: [], paymentPlans: [] }
      }).catch(() => {})
      this.$http.get('/sys/operation-log', { params: { pageNum: 1, pageSize: 10, targetType: 'ct_contract', targetId: this.id } })
        .then(body => { this.logs = (body.data && body.data.records) || [] }).catch(() => {})
    },
    submitReview() { this.$http.post(`/contract/${this.id}/review/submit`).then(() => { this.$message.success('已提交评审'); this.load() }).catch(showError) },
    seal() { this.$http.post(`/contract/${this.id}/seal`, { copyCount: 1 }).then(() => { this.$message.success('已用印'); this.load() }).catch(showError) },
    goFiling() { this.$router.push(`/ct/contract/${this.id}/filing`) },
    aiRisk() { this.$message.info('AI 风险分析入口见台账页（含降级链）') },

    /* ---------- SPRINT-UID-R15：财务与交底闭环（原 4 组端点无 Web 入口） ---------- */
    createInvoice() {
      if (!this.invForm.amount || !this.invForm.invoiceDate) {
        this.$message.warning('金额与开票日期必填')
        return
      }
      this.$http.post('/contract/invoices', { ...this.invForm, contractId: Number(this.id) }).then(() => {
        this.$message.success('开票已登记（待确认）')
        this.invDialog = false
        this.invForm = { invoiceType: 'VAT_SPECIAL' }
        this.load()
      }).catch(showError)
    },
    confirmInvoice(invoice) {
      this.$http.post(`/contract/invoices/${invoice.id}/confirm`).then(() => {
        this.$message.success('开票已确认')
        this.load()
      }).catch(showError)
    },
    createReceipt(andConfirm) {
      if (!this.rcForm.amount || !this.rcForm.receiptDate) {
        this.$message.warning('回款金额与日期必填')
        return
      }
      const finish = msg => {
        this.$message.success(msg)
        this.rcDialog = false
        this.rcForm = { method: 'BANK' }
        this.load()
      }
      this.$http.post('/contract/receipts', { ...this.rcForm, contractId: Number(this.id) }).then(body => {
        const rid = body.data
        if (andConfirm) {
          return this.$http.post(`/contract/receipts/${rid}/confirm`)
            .then(() => finish('回款已登记并确认（回款进度指标取数）'))
        }
        finish('回款已登记（待确认）')
        return null
      }).catch(showError)
    },
    unfreeze() {
      this.$prompt('解除冻结原因（必填）', '解除开票冻结', {
        inputValidator: v => !!(v && v.trim()) || '原因必填',
        confirmButtonText: '解除', cancelButtonText: '取消'
      }).then(({ value }) => {
        this.$http.post(`/contract/${this.id}/unfreeze`, { reason: value.trim() }).then(() => {
          this.$message.success('已解除冻结')
          this.load()
        }).catch(showError)
      }).catch(() => {})
    },
    createDispatch() {
      const receiverIds = (this.dpForm.receiverIds || '').split(',').map(s => Number(s.trim())).filter(n => n > 0)
      if (receiverIds.length === 0) {
        this.$message.warning('请输入接收人ID列表')
        return
      }
      this.$http.post(`/contract/${this.id}/dispatch`, { receiverIds }).then(body => {
        this.$message.success('交底已发送 ' + (body.data || 0) + ' 人（及时率取数）')
        this.dpDialog = false
        this.dpForm = {}
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
.pl-doc-meta { font-size: 12px; color: var(--ink-3); font-variant-numeric: tabular-nums; }
.pl-doc-actions {
  height: 48px; display: flex; align-items: center; gap: 8px; justify-content: flex-end;
  background: var(--surface); border: 1px solid var(--line-2); border-radius: var(--r-3, 6px);
  padding: 0 20px; margin-bottom: 8px;
}
.pl-readonly-tip { font-size: 12px; color: var(--ink-3); margin-right: auto; }
.pl-num { font-variant-numeric: tabular-nums; }
.muted { color: var(--ink-3); font-size: 12px; }
.rel-group { margin-bottom: 10px; }
.fin-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.fin-act { color: var(--pri-500, #1F7B8C); font-weight: 600; }
.fin-act:hover { color: var(--pri-700, #155E6C); }
.rel-title { font-size: 13px; color: var(--ink-2); display: block; margin-bottom: 4px; }
.rel-row { display: flex; gap: 8px; align-items: center; font-size: 12px; padding: 2px 0; }
</style>
