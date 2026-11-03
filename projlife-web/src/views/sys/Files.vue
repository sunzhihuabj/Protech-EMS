<template>
  <!-- 文件管理中心（module-file FileController）：BR-07 仅水印合成件，无原文件返回 -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div slot="header" class="file-head">
        <b>文件上传中心</b>
        <span class="muted">BR-07 仅输出水印合成件；无任何路径返回原文件</span>
      </div>
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: flex-start">
        <!-- 上传区 -->
        <div class="upload-box">
          <div style="font-weight: 600; margin-bottom: 8px">上传文件</div>
          <el-upload
            class="uploader"
            action="/api/v1/files/upload"
            name="file"
            :headers="uploadHeaders"
            :data="{ bizType: bizForm.bizType, bizId: bizForm.bizId }"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
            drag>
            <i class="el-icon-upload"></i>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
          </el-upload>
          <div style="margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap">
            <el-select v-model="bizForm.bizType" size="mini" style="width: 140px">
              <el-option value="COMMON" label="通用" />
              <el-option value="CONTRACT" label="合同附件" />
              <el-option value="PROJECT" label="项目交付物" />
              <el-option value="QC" label="质量报告" />
              <el-option value="FILING" label="建档资料" />
            </el-select>
            <el-input v-model.trim="bizForm.bizId" size="mini" placeholder="业务单号（选填）" style="width: 140px" />
          </div>
        </div>
        <!-- 列表区 -->
        <div class="list-box">
          <div style="font-weight: 600; margin-bottom: 8px">最近上传（按业务类型筛选）</div>
          <div style="display: flex; gap: 8px; margin-bottom: 10px">
            <el-select v-model="listBizType" size="mini" style="width: 140px" clearable placeholder="业务类型">
              <el-option value="COMMON" label="通用" />
              <el-option value="CONTRACT" label="合同附件" />
              <el-option value="PROJECT" label="项目交付物" />
              <el-option value="QC" label="质量报告" />
              <el-option value="FILING" label="建档资料" />
            </el-select>
            <el-input v-model.trim="listBizId" size="mini" placeholder="业务单号" style="width: 160px" clearable />
            <el-button size="mini" type="primary" @click="loadList">查询</el-button>
          </div>
          <el-table :data="files" size="small" v-loading="listLoading" max-height="320" stripe>
            <el-table-column prop="fileName" label="文件名" min-width="200">
              <template slot-scope="{ row }">
                <el-link type="primary" @click="download(row)">{{ row.fileName }}</el-link>
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="90" align="right">
              <template slot-scope="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="bizType" label="类型" width="100" />
            <el-table-column prop="bizId" label="业务号" width="110" />
            <el-table-column prop="uploaderId" label="上传者" width="90" />
            <el-table-column prop="createdAt" label="上传时间" width="160" />
            <el-table-column label="操作" width="120">
              <template slot-scope="{ row }">
                <el-button v-permission="'file:download'" size="mini" type="text" @click="download(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { showError } from '@/api/request'

export default {
  name: 'SysFiles',
  data() {
    return {
      bizForm: { bizType: 'COMMON', bizId: '' },
      listBizType: '', listBizId: '',
      files: [], listLoading: false
    }
  },
  computed: {
    uploadHeaders() {
      const token = localStorage.getItem('pl_token')
      return token ? { Authorization: 'Bearer ' + token } : {}
    }
  },
  methods: {
    beforeUpload(file) {
      if (file.size && file.size > 50 * 1024 * 1024) {
        this.$message.warning('单文件 ≤ 50MB')
        return false
      }
      return true
    },
    onUploadSuccess(resp) {
      if (resp && resp.code === 0) {
        this.$message.success('上传成功')
        // 刷新列表（如果已选 bizType + bizId）
        if (this.listBizType) this.loadList()
      } else {
        this.$message.error('上传失败: ' + (resp && resp.message || '未知'))
      }
    },
    onUploadError() { this.$message.error('上传失败（网络或鉴权异常）') },
    loadList() {
      if (!this.listBizType) { this.$message.info('请先选择业务类型'); return }
      if (!this.listBizId) { this.$message.info('请输入业务单号'); return }
      this.listLoading = true
      this.$http.get('/files', { params: { bizType: this.listBizType, bizId: this.listBizId } })
        .then(body => { this.files = body.data || [] }).catch(showError).finally(() => { this.listLoading = false })
    },
    download(row) {
      // 走 axios 携带 Authorization（拦截器注入 token）；响应为水印合成件二进制
      this.$http.get(`/files/${row.id}/download`, { responseType: 'blob' }).then(blob => {
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = row.fileName || ('file-' + row.id)
        document.body.appendChild(a); a.click(); document.body.removeChild(a)
        URL.revokeObjectURL(url)
      }).catch(showError)
    },
    formatSize(bytes) {
      if (!bytes) return '—'
      if (bytes < 1024) return bytes + 'B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'K'
      return (bytes / 1024 / 1024).toFixed(2) + 'M'
    }
  }
}
</script>

<style scoped>
.file-head { display: flex; align-items: center; gap: 12px; }
.upload-box { width: 340px; flex: none; }
.list-box { flex: 1; min-width: 420px; }
.uploader { width: 100%; }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
