<template>
  <div class="pl-page">
    <el-steps :active="step" simple style="margin-bottom: 16px">
      <el-step title="注册应用" icon="el-icon-key" />
      <el-step title="换取 Token" icon="el-icon-postcard" />
      <el-step title="能力调用" icon="el-icon-cpu" />
    </el-steps>

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <div slot="header"><b>1. 注册（FR-AGT-01，管理端 JWT，secret 仅返一次）</b></div>
          <el-form inline size="small">
            <el-form-item label="平台名"><el-input v-model.trim="regForm.platform" style="width: 160px" /></el-form-item>
            <el-form-item label="身份">
              <el-select v-model="regForm.identity" multiple style="width: 220px" placeholder="EXPERT/WORKER/ASSISTANT">
                <el-option value="EXPERT" /><el-option value="WORKER" /><el-option value="ASSISTANT" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button size="small" type="primary" @click="register">注册</el-button></el-form-item>
          </el-form>
          <el-alert v-if="registered" type="success" :closable="false" show-icon
                    :title="'clientId=' + credential.clientId"
                    description="clientSecret 已在本页暂存（仅注册时返回一次，服务端只存摘要）" />
        </el-card>

        <el-card shadow="never" style="margin-top: 12px">
          <div slot="header"><b>2. 换取会话（client_credentials，2h，WORKER 强制 onBehalfOf）</b></div>
          <el-form inline size="small">
            <el-form-item label="身份">
              <el-select v-model="identity" style="width: 130px" @change="loadCaps">
                <el-option value="EXPERT" /><el-option value="WORKER" /><el-option value="ASSISTANT" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="identity === 'WORKER'" label="代人ID"><el-input v-model.trim="onBehalfOf" style="width: 80px" /></el-form-item>
            <el-form-item><el-button size="small" type="primary" :disabled="!registered" @click="token">获取 Token</el-button></el-form-item>
          </el-form>
          <el-descriptions v-if="session" :column="2" size="small" border>
            <el-descriptions-item label="sessionRef">{{ session.sessionRef.slice(0, 18) }}…</el-descriptions-item>
            <el-descriptions-item label="有效期">{{ session.expiresIn }}s</el-descriptions-item>
            <el-descriptions-item label="能力数" :span="2">{{ (session.capabilities || []).length }} 项（按身份过滤）</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never">
          <div slot="header"><b>3. 能力目录与调用（12 项白名单，禁止类 AGT-FORBIDDEN）</b></div>
          <el-table :data="caps" size="mini" v-loading="capLoading">
            <el-table-column prop="capability" label="能力" min-width="180" />
            <el-table-column prop="mode" label="通道" width="90">
              <template slot-scope="{ row }">
                <el-tag size="mini" :type="row.mode === 'SYNC' ? '' : 'warning'">{{ row.mode === 'SYNC' ? '同步问答' : '异步任务' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template slot-scope="{ row }">
                <el-button size="mini" type="text" :disabled="!session" @click="invoke(row)">{{ row.mode === 'SYNC' ? '调用' : '建任务' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" style="margin-top: 12px">
          <div slot="header"><b>调用日志</b></div>
          <el-empty v-if="logs.length === 0" description="尚无调用" :image-size="50" />
          <el-timeline style="padding-left: 4px">
            <el-timeline-item v-for="(l, i) in logs" :key="i" :timestamp="l.ts"
                              :type="l.ok ? 'success' : 'danger'">
              <b>{{ l.cap }}</b>：{{ l.text }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'

/** Agent 通道前缀与 JWT 通道不同，逐请求覆盖 baseURL */
const AGENT = { baseURL: '' }

export default {
  name: 'AgentConsole',
  data() {
    return {
      step: 0,
      regForm: { platform: '数字员工-演示', identity: ['EXPERT'] },
      registered: false, credential: {},
      identity: 'EXPERT', onBehalfOf: '',
      session: null, caps: [], capLoading: false, logs: []
    }
  },
  methods: {
    log(cap, ok, text) {
      this.logs.unshift({ cap, ok, text, ts: new Date().toLocaleTimeString() })
    },
    register() {
      http.post('/api/agent/v1/register', {
        platform: this.regForm.platform,
        allowedIdentities: this.regForm.identity,
        quotaDaily: 5000
      }, AGENT).then(body => {
        this.credential = body.data
        this.registered = true
        this.step = 1
        this.$notify.success({ title: '注册成功', message: 'clientId=' + body.data.clientId })
      }).catch(showError)
    },
    token() {
      const payload = {
        clientId: this.credential.clientId,
        clientSecret: this.credential.clientSecret,
        identityType: this.identity
      }
      if (this.identity === 'WORKER') payload.onBehalfOf = this.onBehalfOf || undefined
      http.post('/api/agent/v1/token', payload, AGENT).then(body => {
        this.session = body.data
        this.step = 2
        this.loadCaps()
        this.log('token', true, '会话 2h 已建立，能力 ' + body.data.capabilities.length + ' 项')
      }).catch(showError)
    },
    loadCaps() {
      if (!this.session) return
      this.capLoading = true
      http.get('/api/agent/v1/capabilities', { params: { identityType: this.identity }, ...AGENT })
        .then(body => { this.caps = body.data || [] })
        .catch(showError).finally(() => { this.capLoading = false })
    },
    invoke(cap) {
      if (cap.mode === 'SYNC') {
        http.post('/api/agent/v1/messages', {
          sessionRef: this.session.sessionRef,
          capability: cap.capability,
          params: {}
        }, AGENT).then(body => {
          const rows = body.data && body.data.data && body.data.data.rows
          this.log(cap.capability, true, '同步返回 ' + (rows ? rows.length : 0) + ' 行（≤200 行脱敏口径）')
        }).catch(err => this.log(cap.capability, false, blockText(err)))
      } else {
        http.post('/api/agent/v1/tasks', {
          sessionRef: this.session.sessionRef,
          capability: cap.capability,
          params: {}
        }, { ...AGENT, validateStatus: s => s === 202 || s === 200 || s === 422 }).then(resp => {
          const body = resp.data
          if (resp.status === 202 && body.data && body.data.taskId) {
            this.log(cap.capability, true, '202 + code=0，任务 ' + body.data.taskId + ' 已受理')
            this.pollTask(body.data.taskId)
          } else {
            this.log(cap.capability, false, '阻断 ' + (body.data && body.data.blockCode))
          }
        }).catch(err => this.log(cap.capability, false, blockText(err)))
      }
    },
    pollTask(taskId) {
      setTimeout(() => {
        http.get(`/api/agent/v1/tasks/${taskId}`, {
          headers: { 'X-AGT-Session': this.session.sessionRef }, ...AGENT
        }).then(body => {
          this.log(taskId, body.data.status === 'SUCCEEDED', '状态 ' + body.data.status)
        }).catch(err => this.log(taskId, false, blockText(err)))
      }, 600)
    }
  }
}

function blockText(err) {
  const d = (err && err.data) || {}
  return d.blockCode ? ('阻断 ' + d.blockCode + '（422）') : (err.message || '失败')
}
</script>
