<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <el-tab-pane label="九板卡片" name="cards">
        <div class="pl-toolbar">
          <el-button v-permission="'kb:card:manage'" size="small" type="primary" icon="el-icon-plus" @click="cardDialog = true">建卡（五要素，VAGUE 拦截）</el-button>
          <el-button size="small" icon="el-icon-time" @click="escalate">红灯 3 天升级扫描</el-button>
        </div>
        <pt-table :columns="cardCols" :data="cards" :loading="loading"
                  :total="0" aria-label="卡片台账" empty-type="no-data"
                  empty-label="看板卡" empty-action-text="建卡" @empty-action="cardDialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status" :dict="'TODO:待办,GREEN:绿灯,YELLOW:关注,RED:红灯,BLOCKED:阻塞,CLOSED:已闭环'" />
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button v-if="row.status !== 'CLOSED'" v-permission="'kb:card:manage'" size="mini" type="text" @click.stop="block(row)">红灯</el-button>
          <el-button v-if="row.status === 'BLOCKED'" v-permission="'kb:card:manage'" size="mini" type="text" @click.stop="close(row)">三问闭环</el-button>
        </template>
      </pt-table>
      </el-tab-pane>

      <el-tab-pane label="指标灯" name="metrics">
        <div class="pl-toolbar">
          <el-button v-permission="'kb:metric:def'" size="small" type="primary" icon="el-icon-plus" @click="metricDialog = true">指标口径</el-button>
        </div>
        <el-form inline size="small">
          <el-form-item label="指标编码"><el-input v-model.trim="metricCode" style="width: 200px" placeholder="如 S17.MO.DELIVERY" /></el-form-item>
          <el-form-item>
            <el-button v-permission="'kb:metric:def'" size="small" icon="el-icon-search" @click="loadTrend">趋势</el-button>
          </el-form-item>
        </el-form>
        <el-table v-if="trendPeriods.length" :data="trendPeriods" size="small">
          <el-table-column prop="period" label="期间" width="100" />
          <el-table-column prop="metricValue" label="值" width="110" />
          <el-table-column prop="targetValue" label="目标" width="110" />
          <el-table-column prop="status" label="灯色" width="100">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="row.status === 'GREEN' ? 'success' : row.status === 'YELLOW' ? 'warning' : 'danger'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="建卡（五要素：板/标题/责任人/承诺日/口径，VAGUE 标题拦截）" :visible.sync="cardDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="看板ID" required><el-input v-model.trim="cardForm.boardId" /></el-form-item>
        <el-form-item label="标题" required><el-input v-model="cardForm.title" placeholder="具体可验收，避免『跟进客户』式模糊标题" /></el-form-item>
        <el-form-item label="责任人ID" required><el-input v-model.trim="cardForm.ownerId" /></el-form-item>
        <el-form-item label="承诺日" required><el-date-picker v-model="cardForm.promiseDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="cardDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createCard">建卡</el-button>
      </div>
    </el-dialog>

    <el-dialog title="指标口径定义" :visible.sync="metricDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="指标编码" required><el-input v-model.trim="defForm.metricCode" placeholder="如 KB.DELIVERY" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model.trim="defForm.metricName" /></el-form-item>
        <el-form-item label="看板ID" required><el-input v-model.trim="defForm.boardId" /></el-form-item>
        <el-form-item label="目标值"><el-input v-model.trim="defForm.target" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="metricDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createDef">定义</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'Kanban',
  components: { PtTable, PtStatusTag },
  data() {
    return {
cardCols: [
        { prop: 'id', label: '卡', width: 60 },
        { prop: 'boardId', label: '板', width: 60 },
        { prop: 'title', label: '标题', minWidth: 240 },
        { prop: 'ownerId', label: '责任人', width: 80 },
        { prop: 'promiseDate', label: '承诺日', width: 112 },
        { prop: 'status', label: '状态', width: 110 }
      ],
      tab: 'cards',
      cards: [], loading: false,
      cardDialog: false, cardForm: {},
      metricDialog: false, defForm: {}, metricCode: '', trendPeriods: []
    }
  },
  mounted() {
    this.loadCards()
  },
  methods: {
    loadCards() {
      this.loading = true
      http.get('/kb/cards', { params: { pageNum: 1, pageSize: 20 } })
        .then(body => { this.cards = body.data.records || [] })
        .catch(showError).finally(() => { this.loading = false })
    },
    createCard() {
      http.post('/kb/cards', this.cardForm).then(body => {
        this.$message.success('建卡成功 id=' + body.data)
        this.cardDialog = false
        this.loadCards()
      }).catch(showError)
    },
    block(row) {
      this.$prompt('卡点说明（≥5 字，必填）', '红灯卡点（UC-KB-02）')
        .then(({ value }) => http.post(`/kb/cards/${row.id}/block`, { blockReason: value }))
        .then(() => { this.$message.success('已置红灯'); this.loadCards() })
        .catch(showError)
    },
    close(row) {
      this.$prompt('三问闭环：方案（≥5 字）', '过堂闭环（UC-KB-06）')
        .then(({ value }) => http.post(`/kb/cards/${row.id}/close`, { solution: value }))
        .then(() => { this.$message.success('已闭环'); this.loadCards() })
        .catch(showError)
    },
    escalate() {
      http.post('/kb/cards/escalate').then(body => {
        this.$message.success('升级扫描完成：' + (body.data || 0) + ' 张红灯卡升级')
        this.loadCards()
      }).catch(showError)
    },
    createDef() {
      http.post('/kb/metrics', this.defForm).then(() => {
        this.$message.success('指标已定义')
        this.metricDialog = false
      }).catch(showError)
    },
    loadTrend() {
      http.get(`/kb/metrics/${this.metricCode}/trend`).then(body => {
        this.trendPeriods = (body.data && body.data.periods) || []
      }).catch(showError)
    }
  }
}
</script>
