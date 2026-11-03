<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div slot="header"><b>我的看板订阅（FR-KB-16 组合视图）</b></div>
      <el-row :gutter="12" v-loading="loading">
        <el-col :xs="12" :sm="8" :md="8" :lg="6" v-for="b in boards" :key="b.boardCode">
          <el-card shadow="hover" class="kb-my-card" @click.native="go(b)">
            <div class="kb-my-name">{{ b.boardName }}</div>
            <div class="kb-my-meta">{{ b.boardCode }} · {{ b.rhythm || '日板' }}</div>
            <div class="kb-my-level">
              <el-tag v-if="b.boardLevel" size="mini" type="info">{{ b.boardLevel }}</el-tag>
              <el-tag v-if="b.status && b.status !== 'ACTIVE'" size="mini" type="warning">{{ b.status }}</el-tag>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && boards.length === 0"
                description="暂无可访问的看板（需 kb:card:manage 权限，联系管理员授权后刷新）" :image-size="50" />
    </el-card>
  </div>
</template>

<script>
import { showError } from '@/api/request'

/**
 * SPRINT-UID-R15：修复硬编码壳页——原 9 个看板写死在 data()，
 * 现接真实 GET /kb/boards（KbBoard 实体），点击跳转对应九板页。
 */
export default {
  name: 'KbMy',
  data() {
    return {
      boards: [],
      loading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/kb/boards').then(body => { this.boards = body.data || [] })
        .catch(showError).finally(() => { this.loading = false })
    },
    go(b) {
      this.$router.push('/kb/board/' + b.boardCode)
    }
  }
}
</script>

<style scoped>
.kb-my-card { margin-bottom: 12px; cursor: pointer; transition: box-shadow 180ms ease, transform 180ms ease; }
.kb-my-card:hover { transform: translateY(-2px); }
.kb-my-name { font-size: 14px; font-weight: 600; }
.kb-my-meta { font-size: 12px; color: var(--ink-3); margin-top: 4px; }
.kb-my-level { margin-top: 8px; min-height: 20px; }
</style>
