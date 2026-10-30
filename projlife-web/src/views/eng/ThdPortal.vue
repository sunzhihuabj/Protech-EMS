<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 送货单 -->
      <el-tab-pane label="送货单" name="notes">
        <div class="pl-toolbar">
          <el-button v-permission="'thd:portal'" size="small" type="primary" icon="el-icon-plus" @click="noteDialog = true">新建送货单（BR-06 无价格）</el-button>
        </div>
        <el-empty description="送货单经 DN 编号登记后打印留痕（printed_at/by），字段不含价格" :image-size="70" />
      </el-tab-pane>

      <!-- 人员授权 -->
      <el-tab-pane label="人员项目授权" name="grants">
        <div class="pl-toolbar">
          <el-button v-permission="'thd:portal'" size="small" type="primary" icon="el-icon-key" @click="grantDialog = true">授权进场</el-button>
        </div>
        <p class="muted">黑名单生效人员将被拦截（THD-BLACKLISTED）；授权到期自动 EXPIRED（job 扫描）。</p>
      </el-tab-pane>

      <!-- 考核评价 -->
      <el-tab-pane label="考核评价" name="eval">
        <el-card shadow="never">
          <div slot="header"><b>四维评价（均分定级 A≥90 / B≥75 / C≥60 / D）</b></div>
          <el-form inline size="small">
            <el-form-item label="对象类型">
              <el-select v-model="evalForm.targetType" style="width: 120px">
                <el-option value="SUPPLIER" label="供应商" /><el-option value="CREW" label="班组" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象ID"><el-input v-model.trim="evalForm.targetId" style="width: 80px" /></el-form-item>
            <el-form-item label="考核期"><el-input v-model.trim="evalForm.period" placeholder="2026-10" style="width: 100px" /></el-form-item>
            <el-form-item label="质量"><el-input v-model.trim="evalForm.qualityScore" style="width: 70px" /></el-form-item>
            <el-form-item label="交付"><el-input v-model.trim="evalForm.deliveryScore" style="width: 70px" /></el-form-item>
            <el-form-item label="安全"><el-input v-model.trim="evalForm.safetyScore" style="width: 70px" /></el-form-item>
            <el-form-item label="服务"><el-input v-model.trim="evalForm.serviceScore" style="width: 70px" /></el-form-item>
            <el-form-item><el-button v-permission="'thd:portal'" size="small" type="primary" @click="evaluate">提交评价</el-button></el-form-item>
          </el-form>
          <el-descriptions v-if="evalResult" :column="3" size="small" border>
            <el-descriptions-item label="均分">{{ evalResult.totalScore }}</el-descriptions-item>
            <el-descriptions-item label="定级">
              <el-tag size="mini" :type="evalResult.grade === 'A' ? 'success' : evalResult.grade === 'D' ? 'danger' : 'warning'">{{ evalResult.grade }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="口径">同期重复提交视为修正（UK 幂等）</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- 黑名单 -->
      <el-tab-pane label="黑名单" name="blacklist">
        <el-card shadow="never">
          <div slot="header"><b>黑名单管理（原因必填，生效即冻结在场授权）</b></div>
          <el-form inline size="small">
            <el-form-item label="对象类型">
              <el-select v-model="blForm.targetType" style="width: 120px">
                <el-option value="SUPPLIER" label="供应商" /><el-option value="CREW" label="班组" /><el-option value="WORKER" label="人员" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象ID"><el-input v-model.trim="blForm.targetId" style="width: 80px" /></el-form-item>
            <el-form-item label="原因" required><el-input v-model="blForm.reason" style="width: 240px" /></el-form-item>
            <el-form-item><el-button v-permission="'thd:portal'" size="small" type="danger" @click="blacklistAdd">拉黑</el-button></el-form-item>
          </el-form>
          <p class="muted">解除走 /release（留解除日期）；解除后可重新授权进场。</p>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 送货单弹窗 -->
    <el-dialog title="新建送货单" :visible.sync="noteDialog" width="500px">
      <el-form label-width="100px" size="small">
        <el-form-item label="采购订单ID" required><el-input v-model.trim="noteForm.poId" /></el-form-item>
        <el-form-item label="送货日期"><el-date-picker v-model="noteForm.deliveryDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="明细 JSON"><el-input v-model="noteForm.itemsJson" type="textarea" :rows="3" placeholder='[{"material":"M-1","qty":5}]' /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="noteDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createNote">登记并打印</el-button>
      </div>
    </el-dialog>

    <!-- 授权弹窗 -->
    <el-dialog title="人员项目授权" :visible.sync="grantDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="人员ID" required><el-input v-model.trim="grantForm.workerId" /></el-form-item>
        <el-form-item label="项目ID" required><el-input v-model.trim="grantForm.mdProjectId" /></el-form-item>
        <el-form-item label="授权起" required><el-date-picker v-model="grantForm.authStart" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="授权止" required><el-date-picker v-model="grantForm.authEnd" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="grantDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="grant">授权</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'

export default {
  name: 'ThdPortal',
  data() {
    return {
      tab: 'notes',
      noteDialog: false, noteForm: {},
      grantDialog: false, grantForm: {},
      evalForm: { targetType: 'CREW' }, evalResult: null,
      blForm: { targetType: 'WORKER' }
    }
  },
  methods: {
    createNote() {
      http.post('/thd/delivery-notes', this.noteForm).then(body => {
        const note = body.data
        return http.post(`/thd/delivery-notes/${note.id}/print`).then(() => note)
      }).then(note => {
        this.$message.success('送货单 ' + note.noteNo + ' 已登记并打印留痕')
        this.noteDialog = false
      }).catch(showError)
    },
    grant() {
      http.post('/thd/worker-projects', this.grantForm).then(body => {
        this.$message.success('授权 ' + body.data.status + ' 已生效')
        this.grantDialog = false
      }).catch(showError)
    },
    evaluate() {
      http.post('/thd/evaluations', this.evalForm).then(body => {
        this.evalResult = body.data
        this.$message.success('评价已提交：' + body.data.grade + ' 级（均分 ' + body.data.totalScore + '）')
      }).catch(showError)
    },
    blacklistAdd() {
      http.post('/thd/blacklist', this.blForm).then(body => {
        this.$notify.warning({ title: '黑名单已生效', message: '记录 id=' + body.data.id + '，在场授权已自动冻结' })
      }).catch(showError)
    }
  }
}
</script>
