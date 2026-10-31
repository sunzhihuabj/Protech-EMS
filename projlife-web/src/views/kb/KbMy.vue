<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div slot="header"><b>我的看板订阅（FR-KB-16 组合视图）</b></div>
      <el-row :gutter="12">
        <el-col :span="8" v-for="b in boards" :key="b.boardCode">
          <el-card shadow="hover" style="margin-bottom:12px;cursor:pointer" @click.native="$router.push('/kb/board/' + b.boardCode)">
            <div style="font-size:14px;font-weight:600">{{ b.boardName }}</div>
            <div style="font-size:12px;color:var(--ink-3);margin-top:4px">{{ b.boardCode }} · {{ b.rhythm }}</div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="boards.length === 0" description="暂无订阅看板（FR-KB-16）" :image-size="50" />
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'KbMy',
  data() {
    return {
      boards: [
        { boardCode: 'TOTAL', boardName: '经营总板', rhythm: '日取数/周过堂' },
        { boardCode: 'ORDER', boardName: '订单交付', rhythm: '日板' },
        { boardCode: 'SCM', boardName: '供应链', rhythm: '日板' },
        { boardCode: 'MES', boardName: '生产制造', rhythm: '日板' },
        { boardCode: 'ENG', boardName: '工程交付', rhythm: '日板' },
        { boardCode: 'PLM', boardName: '研发', rhythm: '周板' },
        { boardCode: 'SALES', boardName: '市场销售', rhythm: '周板' },
        { boardCode: 'FIN', boardName: '财务', rhythm: '周板' },
        { boardCode: 'ADMIN', boardName: '企管', rhythm: '周板' }
      ]
    }
  }
}
</script>
