<template>
  <!-- 集成运维台（SPRINT-INT1 端点集成：/integ/ops/{dingtalk|shb|tplus}/*） -->
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- ============ 钉钉 ============ -->
      <el-tab-pane label="钉钉通知通道" name="dingtalk">
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div class="health-row">
            <div class="health-item">
              <el-tag :type="dt.enabled ? 'success' : 'info'" effect="dark">{{ dt.enabled ? '已启用' : '已禁用' }}</el-tag>
              <span class="health-label">启用开关</span>
            </div>
            <div class="health-item">
              <el-tag :type="dt.available ? 'success' : 'danger'" effect="dark">{{ dt.available ? '可用' : '不可用' }}</el-tag>
              <span class="health-label">适配器可用</span>
            </div>
            <div class="health-item">
              <el-tag :type="dt.workNotify ? 'success' : 'info'">{{ dt.workNotify ? '已配' : '未配' }}</el-tag>
              <span class="health-label">工作通知</span>
            </div>
            <div class="health-item">
              <el-tag :type="dt.robot ? 'success' : 'info'">{{ dt.robot ? '已配' : '未配' }}</el-tag>
              <span class="health-label">群机器人</span>
            </div>
            <div class="health-item">
              <span class="muted">用户映射 {{ dt.userMappingSize || 0 }} 条</span>
            </div>
          </div>
          <div style="margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap">
            <el-button v-permission="'sys:config'" size="small" type="primary" @click="dtHealth">健康检查</el-button>
            <el-button v-permission="'sys:config'" size="small" type="primary" plain @click="dtTestNotify">测试发送</el-button>
            <el-button v-permission="'sys:config'" size="small" @click="dtRefresh">刷新 Token</el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ============ 售后宝 SHB ============ -->
      <el-tab-pane label="售后宝 SHB" name="shb">
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div class="health-row">
            <div class="health-item">
              <el-tag :type="shb.enabled ? 'success' : 'info'" effect="dark">{{ shb.enabled ? '已启用' : '已禁用' }}</el-tag>
              <span class="health-label">启用开关</span>
            </div>
            <div class="health-item">
              <el-tag :type="shb.configured ? 'success' : 'warning'" effect="dark">{{ shb.configured ? '已配置' : '未配置' }}</el-tag>
              <span class="health-label">凭据齐备</span>
            </div>
            <div class="health-item">
              <el-tag :type="shb.webhookKeySet ? 'success' : 'info'">{{ shb.webhookKeySet ? '已设' : '未设' }}</el-tag>
              <span class="health-label">Webhook 密钥</span>
            </div>
            <div class="health-item">
              <span class="muted">当日取 token {{ shb.todayTokenFetch || 0 }} 次（50/天纪律）</span>
            </div>
            <div class="health-item">
              <span class="muted">Base: {{ shb.baseUrl || '—' }}</span>
            </div>
          </div>
          <div style="margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap">
            <el-button v-permission="'sys:config'" size="small" type="primary" @click="shbHealth">健康检查</el-button>
            <el-button v-permission="'sys:config'" size="small" type="primary" plain @click="shbSync">手动增量对账</el-button>
          </div>
        </el-card>
        <el-card v-if="shbSyncResult" shadow="never" style="margin-top: 12px" :body-style="{ padding: '16px' }">
          <div><b>同步结果</b></div>
          <el-descriptions :column="3" size="small" border style="margin-top:8px">
            <el-descriptions-item label="拉取条数">{{ shbSyncResult.total || 0 }}</el-descriptions-item>
            <el-descriptions-item label="遍历页数">{{ shbSyncResult.pages || 0 }}</el-descriptions-item>
            <el-descriptions-item label="时间窗口">{{ shbSyncResult.timeFilter || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="shbSyncResult.lastError" label="最后错误">
              <span class="danger-text">{{ shbSyncResult.lastError }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- ============ 捷信通 T+ ============ -->
      <el-tab-pane label="捷信通 T+" name="tplus">
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div class="health-row">
            <div class="health-item">
              <el-tag :type="tplus.enabled ? 'success' : 'info'" effect="dark">{{ tplus.enabled ? '已启用' : '已禁用' }}</el-tag>
              <span class="health-label">启用开关</span>
            </div>
            <div class="health-item">
              <el-tag :type="tplus.configured ? 'success' : 'warning'" effect="dark">{{ tplus.configured ? '已配置' : '未配置' }}</el-tag>
              <span class="health-label">凭据齐备</span>
            </div>
            <div class="health-item">
              <el-tag :type="tplus.jwtConfigured ? 'success' : 'info'">{{ tplus.jwtConfigured ? '已设' : '未设' }}</el-tag>
              <span class="health-label">JWT</span>
            </div>
            <div class="health-item">
              <el-tag :type="tplus.probe === 'UP' ? 'success' : 'danger'" effect="dark">
                {{ tplus.probe || '—' }}
              </el-tag>
              <span class="health-label">门户探测</span>
            </div>
            <div class="health-item">
              <span class="muted">Token 模式: {{ tplus.tokenMode || '—' }} · {{ tplus.baseUrl || '—' }}</span>
            </div>
          </div>
          <div style="margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap">
            <el-button v-permission="'sys:config'" size="small" type="primary" @click="tplusHealth">健康检查</el-button>
            <el-button v-permission="'sys:config'" size="small" type="primary" plain @click="tplusQuery">通用查询</el-button>
          </div>
        </el-card>
        <el-card v-if="tplusQueryResult" shadow="never" style="margin-top: 12px" :body-style="{ padding: '16px' }">
          <div><b>查询结果</b></div>
          <pre class="json-pre">{{ prettyJson(tplusQueryResult) }}</pre>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 钉钉测试发送弹窗 -->
    <el-dialog title="钉钉测试发送" :visible.sync="dtTestDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="通道">
          <el-select v-model="dtTest.channel" style="width: 100%">
            <el-option value="AUTO" label="AUTO（适配器全链路 L1→L2 降级）" />
            <el-option value="WORK" label="WORK（工作通知，需 dingUserId）" />
            <el-option value="ROBOT" label="ROBOT（群机器人）" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dtTest.channel === 'AUTO'" label="接收人ID">
          <el-input v-model="dtTest.receiverId" placeholder="数字用户 ID" />
        </el-form-item>
        <el-form-item v-if="dtTest.channel === 'WORK'" label="钉钉用户ID">
          <el-input v-model="dtTest.dingUserId" placeholder="如 dingxxxxxxxx" />
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="dtTest.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="dtTest.content" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dtTestDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doDtTest">发送</el-button>
      </div>
    </el-dialog>

    <!-- T+ 通用查询弹窗 -->
    <el-dialog title="T+ 通用查询透传" :visible.sync="tplusQueryDialog" width="520px">
      <el-form label-width="90px" size="small">
        <el-form-item label="Service"><el-input v-model="tq.service" placeholder="默认 Inventory" /></el-form-item>
        <el-form-item label="Method"><el-input v-model="tq.method" placeholder="默认 Query" /></el-form-item>
        <el-form-item label="Args(JSON)">
          <el-input v-model="tq.args" type="textarea" :rows="4" placeholder='{"code":"..."}' />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="tplusQueryDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doTplusQuery">调用</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { showError } from '@/api/request'
export default {
  name: 'SysMonitor',
  data() {
    return {
      tab: 'dingtalk',
      dt: {}, shb: {}, tplus: {},
      shbSyncResult: null, tplusQueryResult: null,
      dtTestDialog: false, dtTest: { channel: 'AUTO', title: 'Protech 钉钉通道测试', content: '这是一条来自业务总台的测试通知。' },
      tplusQueryDialog: false, tq: { service: 'Inventory', method: 'Query', args: '{"code":""}' }
    }
  },
  mounted() {
    this.loadAll()
  },
  methods: {
    loadAll() { this.dtHealth(); this.shbHealth(); this.tplusHealth() },
    prettyJson(v) {
      try { return JSON.stringify(v, null, 2) } catch (e) { return String(v) }
    },
    dtHealth() {
      this.$http.get('/integ/ops/dingtalk/health').then(b => { this.dt = b.data || {} }).catch(showError)
    },
    dtTestNotify() { this.dtTest = { channel: 'AUTO', title: 'Protech 钉钉通道测试', content: '这是一条来自业务总台的测试通知。', receiverId: '', dingUserId: '' }; this.dtTestDialog = true },
    doDtTest() {
      const body = { channel: this.dtTest.channel, title: this.dtTest.title, content: this.dtTest.content }
      if (this.dtTest.channel === 'AUTO' && this.dtTest.receiverId) body.receiverId = Number(this.dtTest.receiverId)
      if (this.dtTest.channel === 'WORK' && this.dtTest.dingUserId) body.dingUserId = this.dtTest.dingUserId
      this.$http.post('/integ/ops/dingtalk/test-notify', body).then(b => {
        this.$message.success('发送结果: ' + (JSON.stringify(b.data) || 'ok'))
        this.dtTestDialog = false
      }).catch(showError)
    },
    dtRefresh() {
      this.$http.post('/integ/ops/dingtalk/refresh-token').then(b => {
        const d = b.data || {}
        if (d.refreshed) { this.$message.success('Token 已刷新'); this.dtHealth() }
        else this.$message.error('刷新失败: ' + (d.reason || '未知'))
      }).catch(showError)
    },
    shbHealth() {
      this.$http.get('/integ/ops/shb/health').then(b => { this.shb = b.data || {} }).catch(showError)
    },
    shbSync() {
      this.$confirm('手动增量对账将调用 SHB 事件接口并逐条落同步日志（maxPages=10），是否继续？', 'SHB 同步').then(() => {
        this.$http.post('/integ/ops/shb/sync-events', { hours: 24, pageSize: 50, maxPages: 10 }).then(b => {
          this.shbSyncResult = b.data || {}
          this.$message.success(`拉取 ${this.shbSyncResult.total || 0} 条事件`)
        }).catch(showError)
      }).catch(() => {})
    },
    tplusHealth() {
      this.$http.get('/integ/ops/tplus/health').then(b => { this.tplus = b.data || {} }).catch(showError)
    },
    tplusQuery() { this.tq = { service: 'Inventory', method: 'Query', args: '{"code":""}' }; this.tplusQueryDialog = true },
    doTplusQuery() {
      let args = {}
      try { args = JSON.parse(this.tq.args || '{}') } catch (e) { this.$message.warning('Args 不是合法 JSON'); return }
      this.$http.post('/integ/ops/tplus/query', { service: this.tq.service, method: this.tq.method, args }).then(b => {
        this.tplusQueryResult = b.data
        this.$message.success('查询完成')
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.health-row { display: flex; flex-wrap: wrap; gap: 20px; }
.health-item { display: flex; align-items: center; gap: 8px; }
.health-label { font-size: 12px; color: var(--ink-2); }
.muted { color: var(--ink-3); font-size: 12px; }
.danger-text { color: var(--danger, #f56c6c); font-size: 12px; }
.json-pre { background: var(--surface-2, #f7f7f9); border: 1px solid var(--line); border-radius: var(--r-2, 4px); padding: 10px; font-size: 12px; max-height: 320px; overflow: auto; font-family: var(--mono); }
</style>
