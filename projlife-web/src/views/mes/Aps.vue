<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 产能 -->
      <el-tab-pane label="产能台账" name="capacity">
        <el-card shadow="never">
          <div slot="header"><b>资源产能（日产能=工时，A-33 口径）</b></div>
          <el-form inline size="small">
            <el-form-item label="资源ID"><el-input v-model.trim="capForm.resourceId" style="width: 90px" /></el-form-item>
            <el-form-item label="类型">
              <el-select v-model="capForm.resourceType" style="width: 120px">
                <el-option value="EQUIPMENT" label="设备" /><el-option value="PERSON" label="人员" /><el-option value="MOLD" label="模具" />
              </el-select>
            </el-form-item>
            <el-form-item label="日产能(h)"><el-input v-model.trim="capForm.capacityPerDay" style="width: 80px" /></el-form-item>
            <el-form-item><el-button v-permission="'aps:schedule'" size="small" type="primary" @click="addCapacity">登记</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- L1 排产 -->
      <el-tab-pane label="L1 顺排" name="l1">
        <el-card shadow="never">
          <div slot="header"><b>L1 粗排：优先级→订单→工序序顺排，最小负载选资源（FR-APS）</b></div>
          <el-form inline size="small">
            <el-form-item label="订单ID（逗号分隔）"><el-input v-model.trim="l1Form.orderIds" style="width: 240px" placeholder="如 3,4" /></el-form-item>
            <el-form-item label="开始日"><el-date-picker v-model="l1Form.startDate" type="date" value-format="yyyy-MM-dd" style="width: 140px" /></el-form-item>
            <el-form-item><el-button v-permission="'aps:schedule'" size="small" type="primary" @click="runL1">执行排产</el-button></el-form-item>
          </el-form>
          <el-descriptions v-if="l1Result" :column="4" size="small" border>
            <el-descriptions-item label="排产单">{{ l1Result.scheduleNo }}</el-descriptions-item>
            <el-descriptions-item label="明细数">{{ l1Result.detailCount }}</el-descriptions-item>
            <el-descriptions-item label="冲突数">
              <el-tag size="mini" :type="Number(l1Result.conflictCount) > 0 ? 'danger' : 'success'">{{ l1Result.conflictCount }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">{{ l1Result.status }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-for="(c, i) in (l1Result ? l1Result.conflicts : [])" :key="i" style="margin-top: 8px"
                    :title="'冲突：' + c.type + ' — ' + c.message" type="warning" :closable="false" show-icon />
        </el-card>
      </el-tab-pane>

      <!-- 明细与发布 -->
      <el-tab-pane label="明细与发布" name="detail">
        <div class="pl-toolbar">
          <span class="muted">{{ scheduleId ? '排产单 #' + scheduleId : '先执行 L1 排产' }}</span>
          <div class="spacer"></div>
          <el-button v-permission="'aps:schedule'" size="small" type="success" :disabled="!scheduleId" @click="publish">发布（齐套门禁）</el-button>
        </div>
        <el-table :data="details" v-loading="detailLoading" size="small">
          <el-table-column prop="id" label="明细" width="70" />
          <el-table-column prop="productionOrderId" label="订单" width="80" />
          <el-table-column prop="processOrderId" label="工序单" width="90" />
          <el-table-column prop="resourceId" label="资源" width="80" />
          <el-table-column prop="plannedStart" label="计划开始" width="160" />
          <el-table-column prop="plannedEnd" label="计划结束" width="160" />
          <el-table-column prop="runMinutes" label="工时(分)" width="90" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="操作" width="110">
            <template slot-scope="{ row }">
              <el-button v-permission="'aps:schedule'" size="mini" type="text" @click="adjust(row)">拖拽调整</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 调整弹窗 -->
    <el-dialog title="L2 人工调整（排产单置 ADJUSTED、版本 +1）" :visible.sync="adjustDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="adjustForm.plannedStart" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="adjustForm.plannedEnd" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="adjustDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveAdjust">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'

export default {
  name: 'ApsBoard',
  data() {
    return {
      tab: 'capacity',
      capForm: { resourceType: 'EQUIPMENT' },
      l1Form: { orderIds: '', startDate: '' },
      l1Result: null, scheduleId: null,
      details: [], detailLoading: false,
      adjustDialog: false, adjustForm: {}, adjustRow: null
    }
  },
  methods: {
    addCapacity() {
      http.post('/aps/capacities', this.capForm).then(() => {
        this.$message.success('产能已登记')
      }).catch(showError)
    },
    runL1() {
      const ids = this.l1Form.orderIds.split(',').map(s => s.trim()).filter(Boolean)
      if (ids.length === 0) {
        this.$message.warning('请填订单 ID')
        return
      }
      http.post('/aps/schedules/l1', { productionOrderIds: ids, startDate: this.l1Form.startDate || undefined })
        .then(body => {
          this.l1Result = body.data
          this.scheduleId = body.data.scheduleId
          this.loadDetails()
          this.$message.success('排产完成：' + body.data.scheduleNo)
        }).catch(showError)
    },
    loadDetails() {
      if (!this.scheduleId) return
      this.detailLoading = true
      http.get(`/aps/schedules/${this.scheduleId}/details`).then(body => {
        this.details = body.data || []
      }).catch(showError).finally(() => { this.detailLoading = false })
    },
    adjust(row) {
      this.adjustRow = row
      this.adjustForm = {
        plannedStart: (row.plannedStart || '').replace('T', ' ').slice(0, 19),
        plannedEnd: (row.plannedEnd || '').replace('T', ' ').slice(0, 19)
      }
      this.adjustDialog = true
    },
    saveAdjust() {
      http.post(`/aps/details/${this.adjustRow.id}/adjust`, this.adjustForm).then(() => {
        this.$message.success('已调整（版本 +1，ADJUSTED）')
        this.adjustDialog = false
        this.loadDetails()
      }).catch(showError)
    },
    publish() {
      http.post(`/aps/schedules/${this.scheduleId}/publish`).then(body => {
        this.$notify.success({ title: '已发布', message: '排产单 ' + (body.data.scheduleNo || '') + ' → PUBLISHED' })
      }).catch(showError)
    }
  }
}
</script>
