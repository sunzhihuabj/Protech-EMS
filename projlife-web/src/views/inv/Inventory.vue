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
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

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
      slowForm: { responsibility: 'DESIGN' }, disposeDialog: false, disposeForm: {}, disposeRow: null
    }
  },
  mounted() {
    this.loadSlow()
  },
  methods: {
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
