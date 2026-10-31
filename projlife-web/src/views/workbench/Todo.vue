<template>
  <!-- P8 审批待办（UID-03 §2.1）：左侧待办列表 10 列 + 右侧单据只读预览 14 列 + 底部操作条 -->
  <div class="pl-page">
    <el-row :gutter="16">
      <!-- 左：待办列表 -->
      <el-col :span="10">
        <el-card shadow="never" :body-style="{ padding: '12px' }">
          <div slot="header"><b>待办中心（{{ pendingList.length }}）</b></div>
          <div v-if="pendingList.length === 0" class="pt-empty" style="text-align:center;padding:32px 0">
            <i class="el-icon-finished" style="font-size:48px;color:var(--ink-4)"></i>
            <div style="font-size:16px;font-weight:600;color:var(--ink);margin:8px 0 4px">待办已清零</div>
            <div style="font-size:12px;color:var(--ink-3)">所有审批已处理完毕</div>
          </div>
          <div v-for="n in pendingList" :key="n.id" class="todo-item"
               :class="{ 'is-active': selected && selected.id === n.id }"
               @click="selectNode(n)">
            <div class="todo-title">{{ approvalDesc(n.approvalId) }}</div>
            <div class="todo-meta">
              <pt-status-tag :value="'PENDING'" :dict="'PENDING:待审批'" />
              <span class="todo-time">{{ n.createdAt }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右：操作区 -->
      <el-col :span="14">
        <el-card shadow="never" v-if="selected">
          <div slot="header"><b>审批操作</b></div>
          <el-form label-width="80px" size="small">
            <el-form-item label="意见">
              <el-input v-model="comment" type="textarea" :rows="3"
                        placeholder="审批意见（驳回须 ≥10 字）" />
            </el-form-item>
          </el-form>
          <div style="display:flex;gap:8px;flex-wrap:wrap">
            <el-button size="small" type="primary" @click="approve">通过</el-button>
            <el-button size="small" type="danger" plain @click="reject">驳回</el-button>
            <el-button size="small" @click="delegateDialog = true">转办</el-button>
            <el-button size="small" @click="addSignDialog = true">加签</el-button>
          </div>
        </el-card>
        <el-empty v-else description="选择左侧待办查看" :image-size="60" />
      </el-col>
    </el-row>

    <!-- 转办弹窗 -->
    <el-dialog title="转办" :visible.sync="delegateDialog" width="400px">
      <el-form label-width="90px" size="small">
        <el-form-item label="新审批人"><el-input v-model.trim="delegateTo" placeholder="用户 ID" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="delegateDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doDelegate">确认转办</el-button>
      </div>
    </el-dialog>

    <!-- 加签弹窗 -->
    <el-dialog title="加签" :visible.sync="addSignDialog" width="400px">
      <el-form label-width="90px" size="small">
        <el-form-item label="加签人"><el-input v-model.trim="addSignUser" placeholder="用户 ID" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="addSignDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doAddSign">确认加签</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'WorkbenchTodo',
  components: { PtStatusTag },
  data() {
    return {
      pendingList: [], selected: null, comment: '',
      delegateDialog: false, delegateTo: '',
      addSignDialog: false, addSignUser: ''
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.$http.get('/approval/my-pending').then(body => {
        this.pendingList = body.data || []
      }).catch(showError)
    },
    approvalDesc(approvalId) {
      // 简化：通过 approvalId 反查（生产缓存 approval → targetDesc）
      return '审批 #' + approvalId
    },
    selectNode(n) { this.selected = n },
    approve() {
      this.$http.post(`/approval/nodes/${this.selected.id}/approve`, { comment: this.comment })
        .then(() => { this.$message.success('已通过'); this.selected = null; this.load() })
        .catch(showError)
    },
    reject() {
      if (!this.comment || this.comment.length < 10) {
        this.$message.warning('驳回理由须 ≥10 字（FR-CT-14）')
        return
      }
      this.$http.post(`/approval/nodes/${this.selected.id}/reject`, { reason: this.comment })
        .then(() => { this.$message.success('已驳回'); this.selected = null; this.load() })
        .catch(showError)
    },
    doDelegate() {
      this.$http.post(`/approval/nodes/${this.selected.id}/delegate`, { newApproverId: Number(this.delegateTo) })
        .then(() => { this.$message.success('已转办'); this.delegateDialog = false; this.load() })
        .catch(showError)
    },
    doAddSign() {
      this.$http.post(`/approval/${this.selected.approvalId}/addsign`, { userId: Number(this.addSignUser) })
        .then(() => { this.$message.success('已加签'); this.addSignDialog = false; this.load() })
        .catch(showError)
    }
  }
}
</script>

<style scoped>
.todo-item {
  padding: 10px 12px; border-radius: var(--r-2, 4px); cursor: pointer;
  border: 1px solid transparent; margin-bottom: 4px;
}
.todo-item:hover { background: var(--row-hover); }
.todo-item.is-active { background: var(--pri-soft); border-color: var(--tag-pr-bd); }
.todo-title { font-size: 14px; color: var(--ink); }
.todo-meta { display: flex; justify-content: space-between; align-items: center; margin-top: 4px; }
.todo-time { font-size: 12px; color: var(--ink-3); font-variant-numeric: tabular-nums; }
</style>
