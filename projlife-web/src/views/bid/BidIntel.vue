<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 数据源 -->
      <el-tab-pane label="数据源" name="sources">
        <div class="pl-toolbar">
          <el-button v-permission="'bid:source:manage'" size="small" type="primary" icon="el-icon-plus" @click="srcDialog = true">登记数据源</el-button>
          <el-button size="small" icon="el-icon-refresh" @click="loadSources">刷新</el-button>
        </div>
        <el-table :data="sources" v-loading="srcLoading" size="small">
          <el-table-column prop="sourceCode" label="源编号" width="130" />
          <el-table-column prop="category" label="类别" width="110" />
          <el-table-column prop="entryUrl" label="入口" min-width="220" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="130">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ srcStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="robotsCheckedAt" label="robots 检查" width="160" />
          <el-table-column label="操作" width="180">
            <template slot-scope="{ row }">
              <el-button v-if="row.status !== 'ACTIVE'" v-permission="'bid:source:manage'" size="mini" type="text"
                         @click="review(row, 'ACTIVE')">启用（robots 通过）</el-button>
              <el-button v-if="row.status === 'ACTIVE'" v-permission="'bid:source:manage'" size="mini" type="text"
                         @click="review(row, 'DISABLED')">停用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 标讯公告 -->
      <el-tab-pane label="标讯公告" name="notices">
        <div class="pl-toolbar">
          <el-select v-model="noticeLevel" size="small" style="width: 130px" clearable placeholder="匹配级别" @change="loadNotices">
            <el-option value="HIGH" label="高匹配" /><el-option value="MEDIUM" label="中匹配" /><el-option value="ARCHIVED" label="归档" />
          </el-select>
          <el-button size="small" type="primary" icon="el-icon-upload2" @click="ingestDialog = true">入库（四层去重）</el-button>
        </div>
        <pt-table :columns="noticeCols" :data="notices" :loading="noticeLoading"
                      :total="0" aria-label="标讯台账" empty-type="no-data"
                      empty-label="标讯公告" empty-action-text="去入库" @empty-action="ingestDialog = true">
          <template slot="cell-status" slot-scope="{ row }">
            <pt-status-tag :value="row.status" :dict="noticeDict" />
          </template>
          <template slot="actions" slot-scope="{ row }">
            <el-button size="mini" type="text" @click.stop="openMatch(row)">打分</el-button>
            <el-button v-if="row.status === 'ACTIVE'" v-permission="'bid:notice:convert'" size="mini" type="text" @click.stop="convert(row)">转商机</el-button>
            <el-button v-if="row.status === 'ACTIVE'" size="mini" type="text" @click.stop="push(row)">推送订阅</el-button>
          </template>
        </pt-table>
      </el-tab-pane>

      <!-- 订阅 -->
      <el-tab-pane label="我的订阅" name="subscribe">
        <el-form inline size="small">
          <el-form-item label="关键词"><el-input v-model="sub.keywords" placeholder='["风电"]' style="width: 200px" /></el-form-item>
          <el-form-item label="区域"><el-input v-model="sub.regions" placeholder='["内蒙古"]' style="width: 160px" /></el-form-item>
          <el-form-item>
            <el-button v-permission="'bid:push:subscribe'" size="small" type="primary" @click="saveSub">保存订阅</el-button>
          </el-form-item>
        </el-form>
        <p class="muted">命中订阅条件的公告将在推送后出现在消息中心（FR-BID-10/11）。</p>
      </el-tab-pane>
    </el-tabs>

    <!-- 登记数据源 -->
    <el-dialog title="登记数据源（FR-BID-01）" :visible.sync="srcDialog" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="类别" required>
          <el-select v-model="srcForm.category" style="width: 100%">
            <el-option value="A_OWNER" label="A 业主官网" /><el-option value="B_OEM" label="B 主机厂" />
            <el-option value="C_PORTAL" label="C 公共平台" /><el-option value="D_MANUAL" label="D 人工录入" />
          </el-select>
        </el-form-item>
        <el-form-item label="入口 URL" required><el-input v-model.trim="srcForm.entryUrl" /></el-form-item>
        <el-form-item label="频次">
          <el-select v-model="srcForm.frequency" style="width: 100%">
            <el-option value="DAILY_2" label="每日 2 次" /><el-option value="DAILY_1" label="每日 1 次" /><el-option value="WEEKLY" label="每周" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="srcDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createSource">登记</el-button>
      </div>
    </el-dialog>

    <!-- 入库 -->
    <el-dialog title="标讯入库（四层去重：URL 归一/内容哈希/跨源合并/变更关联）" :visible.sync="ingestDialog" width="560px">
      <el-form label-width="100px" size="small">
        <el-form-item label="数据源" required>
          <el-select v-model="ingestForm.sourceId" style="width: 100%">
            <el-option v-for="s in sources.filter(x => x.status === 'ACTIVE')" :key="s.id" :value="s.id" :label="s.sourceCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="URL" required><el-input v-model.trim="ingestForm.url" /></el-form-item>
        <el-form-item label="标题" required><el-input v-model.trim="ingestForm.title" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="ingestForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="项目名"><el-input v-model.trim="ingestForm.projectName" /></el-form-item>
        <el-form-item label="预算(元)"><el-input v-model.trim="ingestForm.budgetAmount" /></el-form-item>
        <el-form-item label="截止时间"><el-date-picker v-model="ingestForm.deadlineAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="区域"><el-input v-model.trim="ingestForm.region" /></el-form-item>
        <el-form-item label="业主ID"><el-input v-model.trim="ingestForm.ownerId" placeholder="归并后的客户主体 id" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="ingestDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="ingest">入库</el-button>
      </div>
    </el-dialog>

    <!-- 打分 -->
    <el-drawer :visible.sync="matchDrawer" size="45%" :title="matchRow ? matchRow.title : '匹配打分'">
      <div style="padding: 0 16px">
        <el-descriptions v-if="match" :column="1" size="small" border>
          <el-descriptions-item label="总分">
            <el-tag :type="match.level === 'HIGH' ? 'success' : match.level === 'MEDIUM' ? 'warning' : 'info'">
              {{ match.score }}（{{ match.level }}）
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="规则版本">{{ match.ruleVersion }}</el-descriptions-item>
          <el-descriptions-item label="维度明细"><pre style="margin:0;white-space:pre-wrap;font-size:12px">{{ pretty(match.breakdown) }}</pre></el-descriptions-item>
          <el-descriptions-item label="回训校准">{{ match.isCorrect === null ? '未反馈' : (match.isCorrect === 1 ? '有效（is_correct=1）' : '误判（is_correct=0）') }}</el-descriptions-item>
        </el-descriptions>
        <div class="pl-toolbar" style="margin-top: 12px">
          <el-button size="mini" type="success" @click="feedback('EFFECTIVE')">反馈有效</el-button>
          <el-button size="mini" type="danger" @click="feedback('IRRELEVANT')">反馈误判</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'BidIntel',
  components: { PtTable, PtStatusTag },
  data() {
    return {
noticeCols: [
        { prop: 'noticeCode', label: '公告编号', width: 150 },
        { prop: 'title', label: '标题', minWidth: 240 },
        { prop: 'region', label: '区域', width: 90 },
        { prop: 'budgetAmount', label: '预算(元)', type: 'money', align: 'right' },
        { prop: 'deadlineAt', label: '截止', width: 160 },
        { prop: 'status', label: '状态', width: 110 }
      ],
      noticeDict: 'ACTIVE:有效,SUPERSEDED:已被变更,CONVERTED:已转商机,CLOSED:已关闭,EXCLUDED:已排除',
      tab: 'notices',
      sources: [], srcLoading: false, srcDialog: false,
      srcForm: { category: 'A_OWNER', frequency: 'DAILY_2' },
      notices: [], noticeLoading: false, noticeLevel: '',
      ingestDialog: false, ingestForm: {},
      sub: { keywords: '', regions: '' },
      matchDrawer: false, matchRow: null, match: null
    }
  },
  mounted() {
    this.loadSources()
    this.loadNotices()
  },
  methods: {
    srcStatus(s) { return { PENDING_REVIEW: '待审核', ACTIVE: '抓取中', DISABLED: '已停用' }[s] || s },
    noticeStatus(s) { return { ACTIVE: '有效', SUPERSEDED: '已被变更', CONVERTED: '已转商机', CLOSED: '已关闭', EXCLUDED: '已排除' }[s] || s },
    pretty(json) {
      try { return JSON.stringify(JSON.parse(json), null, 2) } catch (e) { return json }
    },
    loadSources() {
      this.srcLoading = true
      http.get('/bid/sources', { params: { pageNum: 1, pageSize: 50 } })
        .then(body => { this.sources = body.data.records || [] })
        .catch(showError).finally(() => { this.srcLoading = false })
    },
    createSource() {
      http.post('/bid/sources', this.srcForm).then(body => {
        this.$message.success('已登记 ' + body.data.sourceCode + '（待 robots 审核启用）')
        this.srcDialog = false
        this.loadSources()
      }).catch(showError)
    },
    review(row, status) {
      const robotsCode = status === 'ACTIVE' ? 200 : null
      http.post(`/bid/sources/${row.id}/review`, { status, robotsStatusCode: robotsCode }).then(() => {
        this.$message.success(status === 'ACTIVE' ? '已启用（robots 留痕）' : '已停用')
        this.loadSources()
      }).catch(showError)
    },
    loadNotices() {
      this.noticeLoading = true
      http.get('/bid/notices', { params: { pageNum: 1, pageSize: 20, level: this.noticeLevel || undefined } })
        .then(body => { this.notices = body.data.records || [] })
        .catch(showError).finally(() => { this.noticeLoading = false })
    },
    ingest() {
      const payload = { ...this.ingestForm }
      if (!payload.ownerId) delete payload.ownerId
      http.post('/bid/ingest', payload).then(body => {
        const d = body.data || {}
        this.$notify({ title: '入库结果：' + d.dedup, message: (d.noticeCode || '') + ' ' + dedupText(d.dedup), type: d.dedup === 'NEW' ? 'success' : 'warning' })
        this.ingestDialog = false
        this.loadNotices()
      }).catch(showError)
      function dedupText(t) {
        return { NEW: '新增公告', HASH_DUPLICATE: '内容哈希命中（层2）', URL_DUPLICATE: 'URL 归一命中（层1）', EXACT_MERGED: '跨源合并（层3）' }[t] || t
      }
    },
    openMatch(row) {
      this.matchRow = row
      this.matchDrawer = true
      http.get(`/bid/notices/${row.id}/match`).then(body => { this.match = body.data }).catch(showError)
    },
    feedback(type) {
      http.post('/bid/feedback', { noticeId: this.matchRow.id, feedbackType: type, note: '门户反馈' }).then(() => {
        this.$message.success('反馈已回训（is_correct 更新）')
        this.openMatch(this.matchRow)
      }).catch(showError)
    },
    convert(row) {
      http.post(`/bid/notices/${row.id}/convert`).then(body => {
        const d = body.data || {}
        this.$notify.success({ title: '已转商机（FR-BID-12）', message: '商机 id=' + d.opportunityId + '，source_type=BID 已写回' })
        this.loadNotices()
      }).catch(showError)
    },
    push(row) {
      http.post(`/bid/notices/${row.id}/push`).then(body => {
        const d = body.data || {}
        this.$message.success('已推送 ' + (d.sent || 0) + ' 位订阅用户')
      }).catch(showError)
    },
    saveSub() {
      http.post('/bid/subscribe', { keywords: this.sub.keywords, regions: this.sub.regions }).then(() => {
        this.$message.success('订阅已保存')
      }).catch(showError)
    }
  }
}
</script>
