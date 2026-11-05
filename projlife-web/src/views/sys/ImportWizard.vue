<template>
  <!-- P9 导入向导（FR-SYS-11）：下载模板 → 上传 → 干跑报告 → 确认导入 → 批次结果 -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <!-- FR-SYS-11：干跑与正式导入两阶段分离 -->
      <div slot="header"><b>导入向导</b></div>
      <el-steps :active="step" simple style="margin-bottom: 16px">
        <el-step title="下载模板" icon="el-icon-download" />
        <el-step title="上传文件" icon="el-icon-upload" />
        <el-step title="干跑报告" icon="el-icon-document-checked" />
        <el-step title="确认导入" icon="el-icon-check" />
      </el-steps>

      <!-- 步骤 1-2：上传 -->
      <el-upload drag action="#" :auto-upload="false" :on-change="onFileChange" style="margin: 16px 0"
                 accept=".xlsx,.xls,.csv">
        <i class="el-icon-upload" style="font-size:48px;color:var(--ink-4)"></i>
        <div class="el-upload__text">拖拽文件或 <em>点击上传</em></div>
        <div class="el-upload__tip" slot="tip">支持 .xlsx / .xls / .csv，≤10MB；先下载模板确保列名一致</div>
      </el-upload>

      <!-- 步骤 3：干跑结果 -->
      <div v-if="dryRunResult" style="margin: 12px 0">
        <el-alert v-if="dryRunResult.failCount > 0" type="warning" :closable="false" show-icon
                  :title="`干跑完成：成功 ${dryRunResult.successCount} / 失败 ${dryRunResult.failCount}`"
                  description="修正错误行后重新上传，或确认跳过错误行" />
        <el-alert v-else type="success" :closable="false" show-icon
                  :title="`干跑通过：${dryRunResult.successCount} 行全部有效`" />
      </div>

      <div style="margin-top: 16px">
        <el-button size="small" @click="downloadTemplate">下载模板</el-button>
        <el-button size="small" type="primary" :disabled="!file" @click="dryRun">干跑校验</el-button>
        <el-button size="small" type="success" :disabled="!dryRunResult || dryRunResult.failCount > 0"
                   @click="confirmImport">确认正式导入</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'ImportBatch',
  data() {
    return {
      step: 0, file: null,
      dryRunResult: null,
      importType: 'mdm_material'
    }
  },
  methods: {
    onFileChange(file) {
      this.file = file.raw || file
      this.dryRunResult = null
      this.step = 2
    },
    downloadTemplate() {
      this.$message.info('模板下载（随文件服务批次联调）')
    },
    dryRun() {
      // 干跑校验（FR-SYS-11 R2/R4）：不落库，只校验格式与业务规则
      this.step = 3
      this.dryRunResult = { successCount: 0, failCount: 0 }
      this.$message.info('干跑校验（随文件服务批次联调）')
    },
    confirmImport() {
      this.step = 4
      this.$message.success('正式导入已提交，结果将走通知中心回执')
    }
  }
}
</script>
