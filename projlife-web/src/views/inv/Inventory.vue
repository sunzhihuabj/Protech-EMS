<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 库存事务 -->
      <el-tab-pane label="库存事务" name="txn">
        <div class="pl-toolbar">
          <el-button v-permission="'inv:scan'" size="small" type="primary" icon="el-icon-plus" @click="txnDialog = true">提交事务（T+ 幂等）</el-button>
          <el-button v-permission="'inv:count:manage'" size="small" icon="el-icon-refresh-right" @click="retryExceptions">重试异常事务</el-button>
        </div>
        <el-form inline size="small">
          <el-form-item label="法人"><el-input v-model="availQuery.legalEntityId" style="width: 90px" /></el-form-item>
          <el-form-item label="仓库"><el-input v-model="availQuery.warehouseCode" style="width: 110px" /></el-form-item>
          <el-form-item label="物料"><el-input v-model="availQuery.materialId" style="width: 90px" /></el-form-item>
          <el-form-item><el-button size="small" icon="el-icon-search" @click="loadAvailable">查可用量</el-button></el-form-item>
        </el-form>
        <el-descriptions v-if="available !== null" :column="3" size="small" border style="margin-bottom: 12px">
          <el-descriptions-item label="账面量">{{ available.bookQty }}</el-descriptions-item>
          <el-descriptions-item label="锁定量">{{ available.lockedQty }}</el-descriptions-item>
          <el-descriptions-item label="可用量"><b>{{ available.availableQty }}</b></el-descriptions-item>
        </el-descriptions>
        <p class="muted">事务经唯一幂等键提交，T+1 与财务对账（FR-INV-33~36）。</p>
      </el-tab-pane>

      <!-- 盘点 -->
      <el-tab-pane label="盘点" name="count">
        <div class="pl-toolbar">
          <el-button v-permission="'inv:count:manage'" size="small" type="primary" icon="el-icon-plus" @click="countDialog = true">新建盘点单</el-button>
        </div>
        <el-table :data="countLines" v-loading="countLoading" size="small">
          <el-table-column prop="id" label="行ID" width="70" />
          <el-table-column prop="materialId" label="物料" width="90" />
          <el-table-column prop="bookQty" label="账面" width="100" />
          <el-table-column prop="actualQty" label="实盘" width="100" />
          <el-table-column prop="diffQty" label="差异" width="100">
            <template slot-scope="{ row }">
              <span v-if="row.diffQty !== null" :style="{ color: Number(row.diffQty) < 0 ? '#f56c6c' : '#67c23a' }">{{ row.diffQty }}</span>
              <span v-else class="muted">未盘</span>
            </template>
          </el-table-column>
          <el-table-column prop="cause" label="归因(E-37)" width="160" />
        </el-table>
        <div class="pl-toolbar" style="margin-top: 8px">
          <el-button v-permission="'inv:count:manage'" size="mini" :disabled="!countId" @click="startCount">开始盘点</el-button>
          <el-button v-permission="'inv:count:manage'" size="mini" type="warning" :disabled="!countId" @click="reviewCount">复核生成差异单</el-button>
        </div>
      </el-tab-pane>

      <!-- 呆滞 -->
      <el-tab-pane label="呆滞处置" name="slow">
        <div class="pl-toolbar">
          <el-button v-permission="'inv:slow:manage'" size="small" type="primary" icon="el-icon-plus" @click="slowDialog = true">登记呆滞</el-button>
          <el-button size="small" icon="el-icon-refresh" @click="loadSlow">刷新</el-button>
        </div>
        <el-table :data="slowList" v-loading="slowLoading" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="materialId" label="物料" width="90" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column prop="ageDays" label="库龄" width="80" />
          <el-table-column prop="responsibility" label="责任(E-38)" width="140" />
          <el-table-column prop="status" label="状态" width="120">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="row.status === 'DISPOSED' ? 'info' : row.status === 'CANDIDATE' ? 'warning' : ''">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240">
            <template slot-scope="{ row }">
              <el-button v-if="row.status === 'CANDIDATE'" size="mini" type="text" @click="confirmSlow(row)">确认（需归因）</el-button>
              <el-button v-if="row.status === 'CONFIRMED'" size="mini" type="text" @click="dispose(row)">发起处置</el-button>
            </template>
          </el-table>
        </el-table>

        <!-- SPRINT-UID-R15：处置单台账（审批 L1/L2/L3 + 完成回填）——修复"发起后无审批/完成入口"断链 -->
        <h4 class="inv-sub">处置单（DP 编号 · E-39 方式 · A-31 分级审批）</h4>
        <el-table :data="disposalList" v-loading="disposalLoading" size="small" border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="disposalNo" label="处置单号" width="150" />
          <el-table-column prop="slowMovingId" label="呆滞ID" width="80" />
          <el-table-column prop="method" label="处置方式" width="110" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column prop="expectAmount" label="预计金额" width="100" align="right" />
          <el-table-column prop="actualAmount" label="实际金额" width="100" align="right">
            <template slot-scope="{ row }">{{ row.actualAmount == null ? '—' : row.actualAmount }}</template>
          </el-table-column>
          <el-table-column prop="approvalLevel" label="审批级" width="90">
            <template slot-scope="{ row }">{{ row.approvalLevel || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template slot-scope="{ row }">
              <el-tag v-if="row.disposedAt" size="mini" type="success">已完成</el-tag>
              <el-tag v-else-if="row.approvedAt" size="mini" type="warning">待完成</el-tag>
              <el-tag v-else size="mini" type="danger">待审批</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template slot-scope="{ row }">
              <el-button v-if="!row.approvedAt" size="mini" type="text" class="inv-act"
                         v-permission="'inv:slow:manage'" @click="approveDisposal(row)">审批</el-button>
              <el-button v-if="row.approvedAt && !row.disposedAt" size="mini" type="text" class="inv-act"
                         v-permission="'inv:slow:manage'" @click="completeDisposal(row)">完成</el-button>
              <span v-if="row.disposedAt" class="pl-empty-val">—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- SPRINT-UID-R15：补货管理（BR-05 全链：申请→审批→发料→回填）——修复整块流程无 Web 入口 -->
      <el-tab-pane label="补货" name="replenish">
        <div class="pl-toolbar">
          <el-button v-permission="'inv:replenish:create'" size="small" type="primary" icon="el-icon-plus" @click="rpDialog = true">补货申请</el-button>
          <el-select v-model="rpStatus" size="small" style="width:140px" @change="loadReplenish">
            <el-option value="" label="全部状态" />
            <el-option value="PENDING" label="待审批" />
            <el-option value="APPROVED" label="已审批" />
            <el-option value="ISSUED" label="已发料" />
            <el-option value="BACKFILLED" label="已回填" />
            <el-option value="LOCKED" label="已锁定" />
          </el-select>
          <el-button size="small" icon="el-icon-refresh" @click="loadReplenish">刷新</el-button>
        </div>
        <el-table :data="rpList" v-loading="rpLoading" size="small" border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="replenishNo" label="补货单号" width="150" />
          <el-table-column prop="mdProjectId" label="项目ID" width="90" />
          <el-table-column prop="urgency" label="紧急度" width="90">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="row.urgency === 'URGENT' ? 'danger' : row.urgency === 'HIGH' ? 'warning' : 'info'">{{ row.urgency }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
          <el-table-column prop="amount" label="金额" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="110">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="row.status === 'BACKFILLED' ? 'success' : row.status === 'LOCKED' ? 'info' : 'warning'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template slot-scope="{ row }">
              <el-button v-if="row.status === 'PENDING'" size="mini" type="text" class="inv-act"
                         v-permission="'inv:replenish:approve'" @click="approveReplenish(row)">审批</el-button>
              <el-button v-if="row.status === 'APPROVED'" size="mini" type="text" class="inv-act"
                         v-permission="'inv:scan'" @click="issueReplenish(row)">发料</el-button>
              <el-button v-if="row.status === 'ISSUED'" size="mini" type="text" class="inv-act"
                         v-permission="'inv:replenish:create'" @click="backfillReplenish(row)">回填</el-button>
              <span v-if="row.status === 'BACKFILLED' || row.status === 'LOCKED'" class="pl-empty-val">—</span>
            </template>
          </el-table-column>
        </el-table>
        <p class="muted">BR-05：发料后 25h 内未回填将由 job 锁定（lock-overdue）；审批→发料→回填全链留痕。</p>
      </el-tab-pane>
    </el-tabs>

    <!-- 补货申请弹窗 -->
    <el-dialog title="补货申请（BR-05）" :visible.sync="rpDialog" width="560px">
      <el-form label-width="100px" size="small">
        <el-form-item label="项目ID" required><el-input v-model.trim="rpForm.mdProjectId" style="width: 200px" /></el-form-item>
        <el-form-item label="紧急度">
          <el-select v-model="rpForm.urgency" style="width: 200px">
            <el-option value="NORMAL" label="NORMAL 普通" /><el-option value="HIGH" label="HIGH 高" /><el-option value="URGENT" label="URGENT 紧急" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" required><el-input v-model="rpForm.reason" /></el-form-item>
        <el-form-item label="金额(元)"><el-input-number v-model="rpForm.amount" :min="0" style="width: 200px" /></el-form-item>
        <el-form-item label="明细行">
          <div v-for="(line, idx) in rpForm.lines" :key="idx" style="display:flex;gap:8px;margin-bottom:6px">
            <el-input v-model.trim="line.materialId" placeholder="物料ID" style="width: 120px" />
            <el-input-number v-model="line.qty" :min="1" placeholder="数量" style="width: 130px" />
            <el-input v-model.trim="line.useLocation" placeholder="使用地点" style="flex:1" />
            <el-button size="mini" type="text" class="inv-act" @click="rpForm.lines.splice(idx, 1)">删除</el-button>
          </div>
          <el-button size="small" icon="el-icon-plus" @click="rpForm.lines.push({ materialId: '', qty: 1, useLocation: '' })">加一行</el-button>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="rpDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createReplenish">提交申请</el-button>
      </div>
    </el-dialog>

    <!-- 事务弹窗 -->
    <el-dialog title="库存事务（幂等键必填）" :visible.sync="txnDialog" width="500px">
      <el-form label-width="110px" size="small">
        <el-form-item label="事务类型" required>
          <el-select v-model="txnForm.txnType" style="width: 100%">
            <el-option value="IN_PURCHASE" label="采购入库" /><el-option value="OUT_ISSUE" label="生产领料" />
            <el-option value="IN_RETURN" label="退料入库" /><el-option value="OUT_SALE" label="销售出库" />
          </el-select>
        </el-form-item>
        <el-form-item label="幂等键" required><el-input v-model.trim="txnForm.idempotencyKey" placeholder="唯一，重复提交幂等返回" /></el-form-item>
        <el-form-item label="法人ID" required><el-input v-model.trim="txnForm.legalEntityId" /></el-form-item>
        <el-form-item label="仓库编码" required><el-input v-model.trim="txnForm.warehouseCode" placeholder="如 WH01" /></el-form-item>
        <el-form-item label="物料ID" required><el-input v-model.trim="txnForm.materialId" /></el-form-item>
        <el-form-item label="数量" required><el-input v-model.trim="txnForm.qty" /></el-form-item>
        <el-form-item label="批次"><el-input v-model.trim="txnForm.batchNo" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="txnDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitTxn">提交</el-button>
      </div>
    </el-dialog>

    <!-- 盘点弹窗 -->
    <el-dialog title="新建盘点单（CT 编号，A/B/C/FULL）" :visible.sync="countDialog" width="560px">
      <el-form label-width="90px" size="small">
        <el-form-item label="类型" required>
          <el-select v-model="countForm.countType" style="width: 100%">
            <el-option value="A" label="A 类" /><el-option value="B" label="B 类" />
            <el-option value="C" label="C 类" /><el-option value="FULL" label="全面 FULL" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点行" required>
          <el-table :data="countFormLines" size="mini">
            <el-table-column width="90"><template slot-scope="{ row }"><el-input v-model.trim="row.materialId" size="mini" placeholder="物料ID" /></template></el-table-column>
            <el-table-column width="100"><template slot-scope="{ row }"><el-input v-model.trim="row.bookQty" size="mini" placeholder="账面量" /></template></el-table-column>
            <el-table-column width="50"><template slot-scope="{ $index }"><el-button type="text" size="mini" icon="el-icon-delete" @click="countFormLines.splice($index, 1)" /></template></el-table-column>
          </el-table>
          <el-button type="text" size="mini" icon="el-icon-plus" @click="countFormLines.push({})">加行</el-button>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="countDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createCount">创建</el-button>
      </div>
    </el-dialog>

    <!-- 录入实盘 -->
    <el-dialog title="录入实盘（无归因不调账 E-37）" :visible.sync="entryDialog" width="520px">
      <el-table :data="entryLines" size="mini">
        <el-table-column prop="id" label="行" width="60" />
        <el-table-column prop="materialId" label="物料" width="80" />
        <el-table-column width="110"><template slot-scope="{ row }"><el-input v-model.trim="row.actualQty" size="mini" placeholder="实盘量" /></template></el-table-column>
        <el-table-column>
          <template slot-scope="{ row }">
            <el-select v-model="row.cause" size="mini" placeholder="归因" clearable style="width: 100%">
              <el-option v-for="c in causes" :key="c" :value="c" :label="c" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button size="small" @click="entryDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveEntry">保存录入</el-button>
      </div>
    </el-dialog>

    <!-- 处置弹窗 -->
    <el-dialog title="发起处置（E-39，DP 编号）" :visible.sync="disposeDialog" width="460px">
      <el-form label-width="110px" size="small">
        <el-form-item label="处置方式" required>
          <el-select v-model="disposeForm.method" style="width: 100%">
            <el-option v-for="m in methods" :key="m" :value="m" :label="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" required><el-input v-model.trim="disposeForm.qty" /></el-form-item>
        <el-form-item label="预期金额"><el-input v-model.trim="disposeForm.expectAmount" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="disposeForm.remark" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="disposeDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createDisposal">发起</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'

const CAUSES = ['DOC_LAG', 'REPLENISH_UNBOOKED', 'MIX_ENTITY', 'PICK_ERROR', 'LOST', 'MEASURE']
const METHODS = ['REUSE', 'SERVICE', 'REMODEL', 'SELL', 'SCRAP', 'CUSTOMER']
const RESPONSIBILITIES = ['DESIGN', 'PURCHASE_OVER', 'PRODUCTION_LEFT', 'ORDER_CANCEL', 'QUALITY', 'HISTORY']

export default {
  name: 'InventoryCenter',
  data() {
    return {
      tab: 'txn',
      causes: CAUSES, methods: METHODS, responsibilities: RESPONSIBILITIES,
      txnDialog: false, txnForm: { txnType: 'IN_PURCHASE' },
      availQuery: { legalEntityId: 1, warehouseCode: 'WH01', materialId: 1 },
      available: null,
      countId: null, countLines: [], countLoading: false,
      countDialog: false, countForm: { countType: 'A' }, countFormLines: [{}],
      entryDialog: false, entryLines: [],
      slowList: [], slowLoading: false, slowDialog: false,
      slowForm: { responsibility: 'DESIGN' }, disposeDialog: false, disposeForm: {}, disposeRow: null,
      // SPRINT-UID-R15：处置单台账 + 补货全链（BR-05）
      disposalList: [], disposalLoading: false,
      rpList: [], rpLoading: false, rpStatus: '', rpDialog: false,
      rpForm: { urgency: 'NORMAL', lines: [{ materialId: '', qty: 1, useLocation: '' }] }
    }
  },
  mounted() {
    this.loadSlow()
    this.loadDisposals()
    this.loadReplenish()
  },
  watch: {
    tab(val) {
      if (val === 'slow') { this.loadSlow(); this.loadDisposals() }
      if (val === 'replenish') this.loadReplenish()
    }
  },
  methods: {
    /** SPRINT-UID-R15：处置单审批（L1/L2/L3 分级） */
    loadDisposals() {
      this.disposalLoading = true
      http.get('/inv/slow-moving/disposals').then(body => { this.disposalList = body.data || [] })
        .catch(showError).finally(() => { this.disposalLoading = false })
    },
    approveDisposal(row) {
      this.$prompt('审批级别（L1 主管 / L2 部门 / L3 总经理，按金额分级 A-31）', `审批处置单 ${row.disposalNo}`, {
        inputPattern: /^L[123]$/, inputErrorMessage: '请输入 L1 / L2 / L3',
        confirmButtonText: '通过', cancelButtonText: '取消'
      }).then(({ value }) => {
        http.post(`/inv/slow-moving/disposals/${row.id}/approve`, { level: value }).then(() => {
          this.$message.success(`已按 ${value} 级审批通过`)
          this.loadDisposals()
        }).catch(showError)
      }).catch(() => {})
    },
    completeDisposal(row) {
      this.$prompt('实际处置金额（回填，元）', `完成处置单 ${row.disposalNo}`, {
        inputPattern: /^\d+(\.\d{1,2})?$/, inputErrorMessage: '请输入金额（最多两位小数）',
        confirmButtonText: '完成', cancelButtonText: '取消'
      }).then(({ value }) => {
        http.post(`/inv/slow-moving/disposals/${row.id}/complete`, { actualAmount: value }).then(() => {
          this.$message.success('处置已完成（呆滞台账置 DISPOSED）')
          this.loadDisposals()
          this.loadSlow()
        }).catch(showError)
      }).catch(() => {})
    },
    /** SPRINT-UID-R15：补货全链（BR-05）：申请→审批→发料→回填 */
    loadReplenish() {
      this.rpLoading = true
      http.get('/inv/replenish', { params: { pageNum: 1, pageSize: 50, status: this.rpStatus || undefined } })
        .then(body => { this.rpList = (body.data && body.data.records) || [] })
        .catch(showError).finally(() => { this.rpLoading = false })
    },
    createReplenish() {
      if (!this.rpForm.mdProjectId || !this.rpForm.reason) {
        this.$message.warning('项目ID与原因必填')
        return
      }
      const lines = (this.rpForm.lines || []).filter(l => l.materialId)
        .map(l => ({ materialId: Number(l.materialId), qty: l.qty, useLocation: l.useLocation || undefined }))
      http.post('/inv/replenish', {
        mdProjectId: Number(this.rpForm.mdProjectId),
        reason: this.rpForm.reason,
        urgency: this.rpForm.urgency,
        amount: this.rpForm.amount || undefined,
        lines
      }).then(body => {
        this.$message.success('补货申请已提交 ' + (body.data || ''))
        this.rpDialog = false
        this.rpForm = { urgency: 'NORMAL', lines: [{ materialId: '', qty: 1, useLocation: '' }] }
        this.loadReplenish()
      }).catch(showError)
    },
    approveReplenish(row) {
      this.$confirm(`审批通过补货单 #${row.id}？通过后可发料。`, '补货审批', {
        type: 'warning', confirmButtonText: '通过', cancelButtonText: '取消'
      }).then(() => {
        http.post(`/inv/replenish/${row.id}/approve`).then(() => {
          this.$message.success('已审批通过')
          this.loadReplenish()
        }).catch(showError)
      }).catch(() => {})
    },
    issueReplenish(row) {
      this.$confirm(`发料补货单 #${row.id}？发料后 25h 内须回填（BR-05）。`, '发料确认', {
        type: 'warning', confirmButtonText: '发料', cancelButtonText: '取消'
      }).then(() => {
        http.post(`/inv/replenish/${row.id}/issue`).then(() => {
          this.$message.success('已发料，请及时回填')
          this.loadReplenish()
        }).catch(showError)
      }).catch(() => {})
    },
    backfillReplenish(row) {
      this.$confirm(`回填补货单 #${row.id} 实际用量？回填后流程闭环。`, '回填确认', {
        type: 'warning', confirmButtonText: '回填', cancelButtonText: '取消'
      }).then(() => {
        http.post(`/inv/replenish/${row.id}/backfill`).then(() => {
          this.$message.success('已回填，补货闭环')
          this.loadReplenish()
        }).catch(showError)
      }).catch(() => {})
    },
    submitTxn() {
      http.post('/inv/transactions', this.txnForm).then(body => {
        const d = body.data || {}
        this.$message.success('事务已受理 ' + (d.txnNo || d.id || ''))
        this.txnDialog = false
      }).catch(showError)
    },
    loadAvailable() {
      http.get('/inv/stock/available', { params: this.availQuery }).then(body => {
        this.available = body.data
      }).catch(showError)
    },
    retryExceptions() {
      http.post('/inv/transactions/retry-exceptions').then(body => {
        this.$message.success('已重试 ' + (body.data || 0) + ' 条')
      }).catch(showError)
    },
    createCount() {
      const lines = this.countFormLines.filter(l => l.materialId && l.bookQty)
        .map(l => ({ materialId: l.materialId, bookQty: l.bookQty }))
      if (lines.length === 0) {
        this.$message.warning('至少一行盘点范围')
        return
      }
      http.post('/inv/counts', { count: this.countForm, lines }).then(body => {
        this.countId = body.data.id
        this.$message.success('盘点单已创建 ' + body.data.countNo)
        this.countDialog = false
        this.loadCountLines()
      }).catch(showError)
    },
    loadCountLines() {
      if (!this.countId) return
      this.countLoading = true
      http.get(`/inv/counts/${this.countId}/lines`).then(body => {
        this.countLines = body.data || []
      }).catch(showError).finally(() => { this.countLoading = false })
    },
    startCount() {
      http.post(`/inv/counts/${this.countId}/start`).then(() => {
        this.$message.success('盘点已开始')
        this.entryLines = this.countLines.map(l => ({ ...l }))
        this.entryDialog = true
      }).catch(showError)
    },
    saveEntry() {
      const actuals = this.entryLines
        .filter(l => l.actualQty !== undefined && l.actualQty !== null && l.actualQty !== '')
        .map(l => ({ id: l.id, actualQty: l.actualQty, cause: l.cause }))
      http.post(`/inv/counts/${this.countId}/entry`, actuals).then(body => {
        this.$message.success('已录入 ' + body.data + ' 行')
        this.entryDialog = false
        this.loadCountLines()
      }).catch(showError)
    },
    reviewCount() {
      http.post(`/inv/counts/${this.countId}/review`).then(body => {
        const diffs = body.data || []
        this.$notify.success({ title: '复核完成', message: '生成差异单 ' + diffs.length + ' 张（无归因不调账已校验）' })
      }).catch(showError)
    },
    loadSlow() {
      this.slowLoading = true
      http.get('/inv/slow-moving').then(body => {
        this.slowList = body.data || []
      }).catch(showError).finally(() => { this.slowLoading = false })
    },
    confirmSlow(row) {
      // E-38 归因在建档时落库，确认仅做状态推进（无归因服务端 422+INV-NO-RESPONSIBILITY）
      http.post(`/inv/slow-moving/${row.id}/confirm`).then(() => {
        this.$message.success('呆滞已确认')
        this.loadSlow()
      }).catch(showError)
    },
    dispose(row) {
      this.disposeRow = row
      this.disposeForm = { method: 'SELL', qty: row.qty }
      this.disposeDialog = true
    },
    createDisposal() {
      http.post(`/inv/slow-moving/${this.disposeRow.id}/disposals`, this.disposeForm).then(body => {
        const d = body.data || {}
        this.$message.success('处置单 ' + d.disposalNo + '（' + d.approvalLevel + '）已发起，待审批')
        this.disposeDialog = false
        this.loadSlow()
      }).catch(showError)
    }
  }
}
</script>
<style scoped>
.inv-sub { margin: 16px 0 8px; font-size: 14px; color: var(--ink); }
.inv-act { color: var(--pri-500, #1F7B8C); font-weight: 600; }
.inv-act:hover { color: var(--pri-700, #155E6C); }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
