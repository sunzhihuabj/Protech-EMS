<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <el-tab-pane label="成本归集" name="items">
        <div class="pl-toolbar">
          <el-button v-permission="'cost:budget'" size="small" type="primary" icon="el-icon-plus" @click="itemDialog = true">归集成本（E-33 七项）</el-button>
        </div>
        <el-form inline size="small">
          <el-form-item label="项目ID"><el-input v-model.trim="projectId" style="width: 90px" /></el-form-item>
          <el-form-item>
            <el-button v-permission="'cost:view'" size="small" icon="el-icon-search" @click="loadSummary">查合计</el-button>
            <el-button v-permission="'cost:view'" size="small" icon="el-icon-warning-outline" @click="loadOverrun">超支预警</el-button>
          </el-form-item>
        </el-form>
        <el-descriptions v-if="summary" :column="4" size="small" border>
          <el-descriptions-item v-for="(v, k) in summary.byType" :key="k" :label="k">{{ v }}</el-descriptions-item>
          <el-descriptions-item label="总计"><b>{{ summary.total }}</b></el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="overrun" :title="'超支预警：' + overrun.level + ' — ' + overrun.message"
                  :type="overrun.level === 'RED' ? 'error' : overrun.level === 'YELLOW' ? 'warning' : 'info'"
                  :closable="false" show-icon style="margin-top: 10px" />
      </el-tab-pane>

      <el-tab-pane label="EVM" name="evm">
        <el-card shadow="never">
          <div slot="header"><b>挣值分析（SPI=EV/PV，CPI=EV/AC）</b></div>
          <el-form inline size="small">
            <el-form-item label="项目ID"><el-input v-model.trim="evmForm.projectId" style="width: 80px" /></el-form-item>
            <el-form-item label="日期"><el-date-picker v-model="evmForm.date" type="date" value-format="yyyy-MM-dd" style="width: 130px" /></el-form-item>
            <el-form-item label="PV"><el-input v-model.trim="evmForm.pv" style="width: 110px" /></el-form-item>
            <el-form-item label="EV"><el-input v-model.trim="evmForm.ev" style="width: 110px" /></el-form-item>
            <el-form-item label="AC"><el-input v-model.trim="evmForm.ac" style="width: 110px" /></el-form-item>
            <el-form-item><el-button v-permission="'cost:budget'" size="small" type="primary" @click="calcEvm">计算并落快照</el-button></el-form-item>
          </el-form>
          <el-descriptions v-if="evmResult" :column="3" size="small" border>
            <el-descriptions-item label="SPI">
              <el-tag size="mini" :type="Number(evmResult.spi) >= 1 ? 'success' : 'danger'">{{ evmResult.spi }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="CPI">
              <el-tag size="mini" :type="Number(evmResult.cpi) >= 1 ? 'success' : 'danger'">{{ evmResult.cpi }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="口径">SPI&lt;1 进度滞后；CPI&lt;1 成本超支</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="成本快照" name="snapshot">
        <el-card shadow="never">
          <div slot="header"><b>手动成本快照（七项归集 + 三口径毛利，按日固化）</b></div>
          <el-form inline size="small">
            <el-form-item label="项目ID"><el-input v-model.trim="snapForm.projectId" style="width: 80px" /></el-form-item>
            <el-form-item label="快照日"><el-date-picker v-model="snapForm.date" type="date" value-format="yyyy-MM-dd" style="width: 130px" /></el-form-item>
            <el-form-item><el-button v-permission="'cost:budget'" size="small" type="primary" icon="el-icon-camera" @click="takeSnapshot">生成快照</el-button></el-form-item>
          </el-form>
          <el-descriptions v-if="snapResult" :column="4" size="small" border>
            <el-descriptions-item label="快照日">{{ snapResult.snapshotDate }}</el-descriptions-item>
            <el-descriptions-item label="材料">{{ snapResult.materialCost }}</el-descriptions-item>
            <el-descriptions-item label="人工">{{ snapResult.laborCost }}</el-descriptions-item>
            <el-descriptions-item label="外协">{{ snapResult.outsourceCost }}</el-descriptions-item>
            <el-descriptions-item label="差旅">{{ snapResult.travelCost }}</el-descriptions-item>
            <el-descriptions-item label="现场">{{ snapResult.siteCost }}</el-descriptions-item>
            <el-descriptions-item label="质量">{{ snapResult.qualityCost }}</el-descriptions-item>
            <el-descriptions-item label="管理">{{ snapResult.mgmtCost }}</el-descriptions-item>
            <el-descriptions-item label="总成本"><b>{{ snapResult.totalCost }}</b></el-descriptions-item>
            <el-descriptions-item label="合同额">{{ snapResult.contractAmount }}</el-descriptions-item>
            <el-descriptions-item label="毛利·合同口径">
              <el-tag size="mini" :type="Number(snapResult.grossMarginContract) >= 0 ? 'success' : 'danger'">{{ marginPct(snapResult.grossMarginContract) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="毛利·阶段口径">{{ marginPct(snapResult.grossMarginStage) }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="选择项目与日期后生成快照（金额与毛利按当日归集口径固化）" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="归集成本（E-33 七项之一）" :visible.sync="itemDialog" width="460px">
      <el-form label-width="110px" size="small">
        <el-form-item label="项目ID" required><el-input v-model.trim="itemForm.projectId" /></el-form-item>
        <el-form-item label="成本项" required>
          <el-select v-model="itemForm.costType" style="width: 100%">
            <el-option v-for="t in types" :key="t" :value="t" :label="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" required><el-input v-model.trim="itemForm.amount" /></el-form-item>
        <el-form-item label="来源" required>
          <el-select v-model="itemForm.sourceType" style="width: 100%">
            <el-option value="MANUAL" label="手工" /><el-option value="MO" label="生产单" /><el-option value="PO" label="采购单" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生日" required><el-date-picker v-model="itemForm.occurredDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="itemDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createItem">归集</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'

export default {
  name: 'CostCenter',
  data() {
    return {
      tab: 'items',
      types: ['MATERIAL', 'LABOR', 'EQUIPMENT', 'SUBCONTRACT', 'DESIGN', 'FINANCE', 'OTHER'],
      projectId: '1',
      summary: null, overrun: null,
      evmForm: { projectId: '1', date: '', pv: '', ev: '', ac: '' }, evmResult: null,
      itemDialog: false, itemForm: { sourceType: 'MANUAL' },
      snapForm: { projectId: '1', date: '' }, snapResult: null
    }
  },
  methods: {
    createItem() {
      http.post('/cost/items', this.itemForm).then(() => {
        this.$message.success('成本已归集')
        this.itemDialog = false
      }).catch(showError)
    },
    loadSummary() {
      http.get(`/cost/project/${this.projectId}/summary`).then(body => {
        this.summary = body.data
      }).catch(showError)
    },
    loadOverrun() {
      http.get(`/cost/project/${this.projectId}/overrun`).then(body => {
        this.overrun = body.data
      }).catch(showError)
    },
    calcEvm() {
      http.post('/cost/evm', this.evmForm).then(body => {
        this.evmResult = body.data
        this.$message.success('EVM 已计算并落快照')
      }).catch(showError)
    },
    takeSnapshot() {
      if (!this.snapForm.projectId) { this.$message.warning('项目ID必填'); return }
      http.post('/cost/snapshot', this.snapForm).then(body => {
        this.snapResult = body.data
        this.$message.success('成本快照已生成')
      }).catch(showError)
    },
    marginPct(v) {
      if (v === null || v === undefined || v === '') return '—'
      return (Number(v) * 100).toFixed(2) + '%'
    }
  }
}
</script>
