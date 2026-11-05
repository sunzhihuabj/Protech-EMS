<template>
  <div class="pl-shell">
    <!-- 侧栏（UID-02 §一：232 展开 / 64 折叠；≥1536 默认展开，1366—1535 默认折叠；仅二级菜单区滚动） -->
    <aside class="pl-aside" :class="{ 'is-collapsed': collapsed }">
      <div class="pl-logo">
        <span class="pl-logo-mark">P</span>
        <span v-if="!collapsed" class="pl-logo-text">Protech</span>
      </div>
      <div class="pl-menu-scroll">
        <el-menu :default-active="$route.path" router unique-opened :collapse="collapsed"
                 background-color="var(--sidebar-bg)" text-color="var(--sidebar-ink-2)"
                 active-text-color="var(--sidebar-ink)" :collapse-transition="false">
          <sidebar-node v-for="menu in menus" :key="menu.permCode" :node="menu" />
        </el-menu>
      </div>
      <button class="pl-collapse-btn" :aria-expanded="!collapsed" aria-label="折叠侧栏"
              @click="toggleCollapse">
        <i :class="collapsed ? 'el-icon-s-unfold' : 'el-icon-s-fold'"></i>
      </button>
    </aside>

    <div class="pl-body">
      <!-- 顶栏（UID-02 §四：56 sticky；左折叠+面包屑，右角标+用户菜单；禁止业务操作） -->
      <header class="pl-header">
        <div class="pl-header-left">
          <button class="pl-icon-btn" aria-label="折叠侧栏" @click="toggleCollapse">
            <i :class="collapsed ? 'el-icon-s-unfold' : 'el-icon-s-fold'"></i>
          </button>
          <el-breadcrumb separator="/" class="pl-crumb">
            <el-breadcrumb-item>工作台</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title && $route.path !== '/workbench'">
              <span aria-current="page">{{ $route.meta.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
          <!-- 全局搜索（并发 4 类：合同号/项目编码/物料码/客户名；每类前 5，≤20 条；/ 聚焦） -->
          <el-select ref="globalSearch" class="pl-search" v-model="searchHit" filterable remote clearable
                     size="small" :remote-method="doSearch" :loading="searching"
                     placeholder="搜索 合同号 / 项目编码 / 物料码 / 客户名（按 /）" popper-class="pl-search-popper"
                     @focus="searchFocused = true" @clear="searchHits = []"
                     @keyup.esc.native="clearSearch">
            <template v-if="searchHits.length">
              <el-option v-for="h in searchHits" :key="h.type + h.id" :value="h.type + ':' + h.id"
                         :label="h.text">
                <span class="pl-search-type">{{ h.typeLabel }}</span>
                <span class="pl-search-text">{{ h.text }}</span>
              </el-option>
            </template>
          </el-select>
        </div>
        <div class="pl-header-right">
          <!-- 待办角标（FR-SYS-14）与消息角标（FR-SYS-13）位置/图标可区分 -->
          <el-tooltip content="待办中心" placement="bottom">
            <el-badge :value="todoCount" :hidden="!todoCount" :max="99" class="pl-badge">
              <i class="el-icon-finished" @click="$router.push('/workbench/message')"></i>
            </el-badge>
          </el-tooltip>
          <el-tooltip content="消息中心" placement="bottom">
            <el-badge :value="unread" :hidden="!unread" :max="99" class="pl-badge">
              <i class="el-icon-bell" @click="$router.push('/workbench/message')"></i>
            </el-badge>
          </el-tooltip>
          <el-dropdown trigger="click" @command="onCommand">
            <span class="pl-user">
              {{ user && user.realName }}（{{ user && user.userCode }}）
              <i class="el-icon-arrow-down"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="workbench">我的工作台</el-dropdown-item>
              <el-dropdown-item command="appearance">外观设置</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item disabled>Protech {{ version }} · {{ envLabel }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区：唯一滚动容器（禁止 body 滚动） -->
      <main class="pl-main">
        <router-view />
      </main>
    </div>

    <!-- 页面水印 W1（UID-01 §八.6：−20°/220×160/12px/10s 位移 24px/覆盖弹窗抽屉/取 --wm） -->
    <div class="pl-watermark" :style="{ backgroundImage: watermarkImage }" aria-hidden="true"></div>
  </div>
</template>

<script>
import SidebarNode from './SidebarNode.vue'
import { showError } from '@/api/request'
import ProtechTheme from '@/theme'

const SIDEBAR_KEY = 'protech:sidebar:collapsed'

export default {
  name: 'MainLayout',
  components: { SidebarNode },
  data() {
    return {
      collapsed: false,
      watermarkImage: '',
      searchHit: '',
      searchHits: [],
      searching: false,
      searchTimer: null,
      todoCount: 0,
      version: 'V1.0',
      envLabel: 'UAT',
      unsubscribeTheme: null
    }
  },
  computed: {
    menus() {
      return this.$store.state.menus || []
    },
    user() {
      return this.$store.state.user
    },
    unread() {
      return this.$store.state.unread
    }
  },
  mounted() {
    // SPRINT-OPS1 F14（UID-02 §四）：/ 聚焦全局搜索，Esc 清空关闭
    this._keyNav = (e) => {
      const tag = (e.target && e.target.tagName) || ''
      const typing = tag === 'INPUT' || tag === 'TEXTAREA' || (e.target && e.target.isContentEditable)
      if (e.key === '/' && !typing) {
        e.preventDefault()
        const ref = this.$refs.globalSearch
        if (ref && ref.focus) { ref.focus() }
      }
    }
    window.addEventListener('keydown', this._keyNav)
    // 折叠默认值：≥1536 展开，1366—1535 折叠（UID-02 §一.1）；用户切换持久化
    const saved = localStorage.getItem(SIDEBAR_KEY)
    this.collapsed = saved !== null
      ? saved === '1'
      : (window.innerWidth < 1536)
    // SPRINT-UID-R15：小屏自适应（≤1024 自动折叠、≥1200 恢复偏好；不覆盖用户持久化选择）
    this._resizeNav = () => {
      const w = window.innerWidth
      if (w <= 1024 && !this._autoCollapsed) {
        this._autoCollapsed = true
        this.collapsed = true
      } else if (w >= 1200 && this._autoCollapsed) {
        this._autoCollapsed = false
        const s = localStorage.getItem(SIDEBAR_KEY)
        this.collapsed = s !== null ? s === '1' : (w < 1536)
      }
    }
    window.addEventListener('resize', this._resizeNav)
    this.renderWatermark()
    // W1 几何：每 10s 单向位移 24px 后复位（UID-01 §八.6）
    this.watermarkTimer = setInterval(this.renderWatermark, 10000)
    // 主题切换 → canvas 水印按 --wm 重绘（UID-04 §三：CSS 变量变化不重绘 canvas）
    if (ProtechTheme && ProtechTheme.subscribe) {
      this.unsubscribeTheme = ProtechTheme.subscribe(() => this.renderWatermark())
    }
    this.loadTodoCount()
  },
  beforeDestroy() {
    clearInterval(this.watermarkTimer)
    if (this.unsubscribeTheme) { this.unsubscribeTheme() }
    if (this._keyNav) { window.removeEventListener('keydown', this._keyNav) }
    if (this._resizeNav) { window.removeEventListener('resize', this._resizeNav) }
  },
  methods: {
    clearSearch() {
      this.searchHit = ''
      this.searchHits = []
      this.searchFocused = false
    },
    toggleCollapse() {
      this.collapsed = !this.collapsed
      try { localStorage.setItem(SIDEBAR_KEY, this.collapsed ? '1' : '0') } catch (e) { /* 忽略 */ }
    },
    onCommand(cmd) {
      if (cmd === 'logout') {
        this.$store.dispatch('logout')
      } else if (cmd === 'appearance') {
        this.$router.push('/profile/appearance')
      } else if (cmd === 'password') {
        this.$router.push('/auth/change-password')
      } else if (cmd === 'workbench') {
        this.$router.push('/workbench')
      }
    },
    /** 全局搜索：并发 4 类各取前 5（UID-02 §四）；结果强制过数据范围（服务端保证） */
    doSearch(keyword) {
      if (this.searchTimer) clearTimeout(this.searchTimer)
      if (!keyword || keyword.length < 2) {
        this.searchHits = []
        return
      }
      this.searchTimer = setTimeout(() => {
        this.searching = true
        const kw = { keyword }
        Promise.all([
          this.$http.get('/contract', { params: { pageNum: 1, pageSize: 5, ...kw } }).catch(() => null),
          this.$http.get('/mdm/projects', { params: { pageNum: 1, pageSize: 5, ...kw } }).catch(() => null),
          this.$http.get('/mdm/materials', { params: { pageNum: 1, pageSize: 5, ...kw } }).catch(() => null),
          this.$http.get('/mdm/customers', { params: { pageNum: 1, pageSize: 5, ...kw } }).catch(() => null)
        ]).then(([ct, prj, mat, cust]) => {
          const hits = []
          const push = (type, typeLabel, resp, no, text) => {
            const rs = (resp && resp.data && resp.data.records) || []
            rs.slice(0, 5).forEach(r => hits.push({ type, typeLabel, id: r.id, text: r[text] || r[no] }))
          }
          push('CT', '合同', ct, 'contractNo', 'contractName')
          push('PRJ', '项目', prj, 'projectCode', 'stdName')
          push('MAT', '物料', mat, 'materialCode', 'materialName')
          push('CUST', '客户', cust, 'customerCode', 'customerName')
          this.searchHits = hits.slice(0, 20)
        }).finally(() => { this.searching = false })
      }, 300)
    },
    loadTodoCount() {
      this.$http.get('/notifications/summary').then(body => {
        this.todoCount = (body.data && body.data.unread) || 0
      }).catch(() => {})
    },
    /** W1 水印：−20° 斜向平铺 220×160、12px、"姓名 工号 时间"，透明度取 --wm（不自定） */
    renderWatermark() {
      const user = this.$store.state.user || {}
      const text = `${user.realName || ''} ${user.userCode || ''} ${new Date().toLocaleString('zh-CN')}`
      const wm = getComputedStyle(document.documentElement).getPropertyValue('--wm').trim()
        || 'rgba(26,33,40,.10)'
      const canvas = document.createElement('canvas')
      canvas.width = 220
      canvas.height = 160
      const ctx = canvas.getContext('2d')
      ctx.rotate(-20 * Math.PI / 180)
      ctx.font = '12px "Microsoft YaHei UI", "Microsoft YaHei", sans-serif'
      ctx.fillStyle = wm
      ctx.fillText(text, 10, 100)
      this.watermarkImage = `url(${canvas.toDataURL('image/png')})`
    }
  }
}
</script>

<style scoped>
.pl-shell { height: 100%; display: flex; overflow: hidden; }
.pl-aside {
  width: 232px; display: flex; flex-direction: column; flex-shrink: 0;
  background: var(--sidebar-bg); transition: width 180ms ease;
}
.pl-aside.is-collapsed { width: 64px; }
.pl-logo {
  height: 56px; display: flex; align-items: center; gap: 8px; padding: 0 16px;
  color: var(--sidebar-ink); font-weight: 700; flex-shrink: 0;
}
.pl-logo-mark {
  width: 28px; height: 28px; border-radius: var(--r-2); background: var(--sidebar-on);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.pl-logo-text { font-size: 16px; letter-spacing: 1px; }
.pl-menu-scroll { flex: 1; overflow-y: auto; overflow-x: hidden; }
.pl-menu-scroll >>> .el-menu { border-right: none; }
/* SPRINT-UI-1 P2：侧栏激活项左侧主色指示条 + 激活/悬停底色（覆盖 Element inline 背景） */
.pl-menu-scroll >>> .el-menu-item.is-active {
  background-color: var(--sidebar-on) !important;
  box-shadow: inset 3px 0 0 var(--accent);
  color: var(--sidebar-ink) !important;
  font-weight: 600;
}
.pl-menu-scroll >>> .el-menu-item:hover {
  background-color: var(--sidebar-on) !important;
  color: var(--sidebar-ink) !important;
}
.pl-collapse-btn {
  height: 40px; border: none; background: transparent; color: var(--sidebar-ink-2);
  cursor: pointer; flex-shrink: 0;
}
.pl-collapse-btn:hover { color: var(--sidebar-ink); background: var(--sidebar-on); }
.pl-body { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.pl-header {
  height: 56px; flex-shrink: 0; display: flex; align-items: center; justify-content: space-between;
  background: var(--surface); border-bottom: 1px solid var(--line);
  position: sticky; top: 0; z-index: 10; padding: 0 16px;
}
.pl-header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.pl-icon-btn {
  width: 40px; height: 40px; border: none; background: transparent; cursor: pointer;
  color: var(--ink-2); font-size: 18px; border-radius: var(--r-2);
}
.pl-icon-btn:hover { background: var(--row-hover); color: var(--ink); }
.pl-crumb { flex-shrink: 0; }
/* SPRINT-UID-R15：搜索框 flex 自适应（原固定 320px 小屏挤压操作区） */
.pl-search { flex: 0 1 320px; min-width: 160px; width: auto; }
.pl-search-type { font-size: 12px; color: var(--ink-3); margin-right: 8px; }
.pl-header-right { display: flex; align-items: center; gap: 20px; }
.pl-badge { cursor: pointer; font-size: 16px; color: var(--ink-2); }
.pl-user { cursor: pointer; color: var(--ink); }
.pl-main {
  flex: 1; overflow-y: auto; background: var(--bg);
  padding: 16px 24px 24px;
}
/* W1 水印层：pointer-events:none、覆盖弹窗与抽屉（z-index 高于浮层） */
.pl-watermark {
  position: fixed; inset: 0; pointer-events: none; z-index: 3000;
  background-repeat: repeat;
}

/* ---------- SPRINT-UID-R15：响应式断点（跨设备适配，UID-02 栅格口径外延） ---------- */
/* ≤1200：搜索框收缩、内容区收边距 */
@media (max-width: 1200px) {
  .pl-search { flex-basis: 220px; }
  .pl-main { padding: 14px 16px 20px; }
  .pl-header { padding: 0 12px; }
}
/* ≤768：隐藏面包屑、搜索框占满剩余空间、角标区收窄（平板/小笔记本） */
@media (max-width: 768px) {
  .pl-crumb { display: none; }
  .pl-search { flex: 1 1 auto; min-width: 130px; }
  .pl-header-right { gap: 12px; }
  .pl-main { padding: 10px 12px 16px; }
  .pl-user { max-width: 110px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
</style>
