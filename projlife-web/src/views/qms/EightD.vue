<template>
  <!-- 8D 报告（FR-QMS-08~13）：新建 + 关闭（D6 校验） -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-button v-permission="'qms:8d:manage'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建 8D</el-button>
      </div>
      <div class="pt-empty" style="text-align:center;padding:48px 0">
        <i class="el-icon-document" style="font-size:48px;color:var(--ink-4)"></i>
        <div class="pt-empty-title" style="font-size:16px;font-weight:600;color:var(--ink);margin:8px 0 4px">暂无 8D 报告</div>
        <div class="pt-empty-hint" style="font-size:12px;color:var(--ink-3);margin-bottom:12px">8D 报告在质量部发起（FR-QMS-08~13）</div>
        <el-button size="small" type="primary" @click="dialog = true">新建第一份 8D</el-button>
      </div>
    </el-card>
    <el-dialog title="新建 8D 报告（D1-D3 填写，D6 关闭校验）" :visible.sync="dialog" width="480px">
      <el-form label-width="110px" size="small">
        <el-form-item label="问题描述" required>
          <el-input v-model="form.problemDesc" type="textarea" :rows="3" placeholder="5W2H 描述问题" />
        </el-form-item>
        <el-form-item label="来源检验ID"><el-input v-model.trim="form.sourceInspectionId" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">发起 8D</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { showError } from '@/api/request'
export default {
  name: 'EightD',
  data() {
    return { dialog: false, form: {} }
  },
  methods: {
    create() {
      this.$http.post('/qms/8ds', this.form).then(body => {
        this.$message.success('8D 报告已发起 ' + body.data)
        this.dialog = false
      }).catch(showError)
    }
  }
}
</script>
