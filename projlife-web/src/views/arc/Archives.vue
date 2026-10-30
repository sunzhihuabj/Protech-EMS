<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <el-tab-pane label="建档" name="archives">
        <div class="pl-toolbar">
          <el-button v-permission="'arc:manage'" size="small" type="primary" icon="el-icon-plus" @click="archDialog = true">建档（PRT 编号）</el-button>
        </div>
        <p class="muted">门类 E-41（HT 合同/TZ 图纸…）× 保管期限 E-42（P10/P30 永久）。</p>
      </el-tab-pane>

      <el-tab-pane label="借阅归还" name="borrow">
        <el-card shadow="never">
          <div slot="header"><b>借阅（在借档案不得重复借出/销毁）</b></div>
          <el-form inline size="small">
            <el-form-item label="档案ID"><el-input v-model.trim="borrowForm.archiveId" style="width: 90px" /></el-form-item>
            <el-form-item label="用途"><el-input v-model="borrowForm.purpose" style="width: 200px" /></el-form-item>
            <el-form-item label="应还日"><el-date-picker v-model="borrowForm.dueDate" type="date" value-format="yyyy-MM-dd" style="width: 130px" /></el-form-item>
            <el-form-item><el-button v-permission="'arc:manage'" size="small" type="primary" @click="borrow">借出</el-button></el-form-item>
          </el-form>
          <p class="muted">借出后可归还；应还日已过由逾期扫描置 OVERDUE（job）。</p>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="销毁移交" name="destroy">
        <el-card shadow="never">
          <div slot="header"><b>销毁（鉴定意见必填 ARC-NO-APPRAISAL）</b></div>
          <el-form inline size="small">
            <el-form-item label="档案ID"><el-input v-model.trim="destroyForm.archiveIds" style="width: 120px" /></el-form-item>
            <el-form-item label="原因"><el-input v-model="destroyForm.reason" style="width: 160px" /></el-form-item>
            <el-form-item label="鉴定意见"><el-input v-model="destroyForm.appraisalOpinion" style="width: 220px" /></el-form-item>
            <el-form-item><el-button v-permission="'arc:manage'" size="small" type="danger" @click="destroy">销毁</el-button></el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never" style="margin-top: 12px">
          <div slot="header"><b>移交（YJ 编号，确认后归入接收部门）</b></div>
          <el-form inline size="small">
            <el-form-item label="档案ID"><el-input v-model.trim="transferForm.archiveIds" style="width: 120px" /></el-form-item>
            <el-form-item label="转入部门"><el-input v-model.trim="transferForm.toDept" style="width: 100px" /></el-form-item>
            <el-form-item><el-button v-permission="'arc:manage'" size="small" type="primary" @click="transfer">发起移交</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="档案建档" :visible.sync="archDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="题名" required><el-input v-model="archForm.title" /></el-form-item>
        <el-form-item label="门类" required>
          <el-select v-model="archForm.category" style="width: 100%">
            <el-option value="HT" label="HT 合同" /><el-option value="TZ" label="TZ 图纸" /><el-option value="JG" label="JG 竣工" />
          </el-select>
        </el-form-item>
        <el-form-item label="保管期限" required>
          <el-select v-model="archForm.retention" style="width: 100%">
            <el-option value="P10" label="P10 十年" /><el-option value="P30" label="P30 三十年" /><el-option value="PERM" label="PERM 永久" />
          </el-select>
        </el-form-item>
        <el-form-item label="年度"><el-date-picker v-model="archYear" type="year" value-format="yyyy" style="width: 100%" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="archDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createArchive">建档</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'

export default {
  name: 'Archives',
  data() {
    return {
      tab: 'archives',
      archDialog: false, archForm: {}, archYear: '2026',
      borrowForm: {}, destroyForm: {}, transferForm: {}
    }
  },
  methods: {
    createArchive() {
      http.post('/arc/archives', { ...this.archForm, archiveYear: Number(this.archYear) }).then(body => {
        this.$message.success('档案已建 ' + body.data.archiveNo)
        this.archDialog = false
      }).catch(showError)
    },
    borrow() {
      http.post('/arc/borrows', {
        ...this.borrowForm,
        borrowerId: 1,
        borrowDate: new Date().toISOString().slice(0, 10)
      }).then(body => {
        this.$message.success('借出 ' + body.data.borrowNo)
      }).catch(showError)
    },
    destroy() {
      http.post('/arc/destroys', this.destroyForm).then(body => {
        this.$message.success('销毁单 ' + body.data.destroyNo + ' 已登记（档案置 DESTROYED）')
      }).catch(showError)
    },
    transfer() {
      http.post('/arc/transfers', {
        ...this.transferForm,
        fromDept: 12,
        transferor: 1,
        receiver: 4,
        transferDate: new Date().toISOString().slice(0, 10)
      }).then(body => {
        const id = body.data.id
        return http.post(`/arc/transfers/${id}/confirm`).then(() => {
          this.$message.success('移交 ' + body.data.transferNo + ' 已确认（档案归入接收部门）')
        })
      }).catch(showError)
    }
  }
}
</script>
