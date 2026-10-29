<template>
  <el-container class="pl-shell">
    <!-- 侧栏：按权限渲染菜单（S0-14 完成标准：换角色导航变化） -->
    <el-aside width="220px" class="pl-aside">
      <div class="pl-logo">ProjLife</div>
      <el-menu :default-active="$route.path" router unique-opened background-color="#1d2b36"
               text-color="#b7c4cd" active-text-color="#ffffff">
        <sidebar-node v-for="menu in menus" :key="menu.permCode" :node="menu" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="pl-header" height="52px">
        <span class="pl-title">{{ $route.meta.title || 'ProjLife' }}</span>
        <div class="pl-header-right">
          <el-badge :value="unread" :hidden="!unread" class="pl-bell">
            <i class="el-icon-bell" @click="$router.push('/notifications')"></i>
          </el-badge>
          <el-dropdown trigger="click" @command="onCommand">
            <span class="pl-user">
              {{ user && user.realName }}（{{ user && user.userCode }}）
              <i class="el-icon-arrow-down"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="appearance">外观设置</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="pl-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 页面水印 W1（03 册 §4.1；验收 UC-SYS-08：10 秒后时间变化） -->
    <div class="pl-watermark" :style="{ backgroundImage: watermarkImage }"></div>

    <!-- 修改密码对话框（首登强改与自助改密共用逻辑） -->
    <el-dialog title="修改密码" :visible.sync="pwdDialog" width="380px">
      <el-form label-width="80px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" @click="submitPassword">确定</el-button>
      </span>
    </el-dialog>
  </el-container>
</template>

<script>
import SidebarNode from './SidebarNode.vue'
import { showError } from '@/api/request'

export default {
  name: 'MainLayout',
  components: { SidebarNode },
  data() {
    return {
      watermarkImage: '',
      pwdDialog: false,
      pwdForm: { oldPassword: '', newPassword: '' }
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
    this.renderWatermark()
    // W1 验收口径：水印时间随时间变化（10 秒刷新）
    this.timer = setInterval(this.renderWatermark, 10000)
  },
  beforeDestroy() {
    clearInterval(this.timer)
  },
  methods: {
    onCommand(cmd) {
      if (cmd === 'logout') {
        this.$store.dispatch('logout')
      } else if (cmd === 'appearance') {
        this.$router.push('/profile/appearance')
      } else if (cmd === 'password') {
        this.promptChangePassword()
      }
    },
    promptChangePassword() {
      this.pwdForm = { oldPassword: '', newPassword: '' }
      this.pwdDialog = true
    },
    submitPassword() {
      if (!this.pwdForm.oldPassword || this.pwdForm.newPassword.length < 8) {
        this.$message.warning('新密码至少 8 位')
        return
      }
      this.$http.put('/auth/me/password', this.pwdForm).then(() => {
        this.$message.success('密码已修改，请重新登录')
        this.pwdDialog = false
        this.$store.dispatch('logout')
      }).catch(showError)
    },
    renderWatermark() {
      const user = this.$store.state.user || {}
      const text = `${user.realName || '未登录'} ${user.userCode || ''} ${new Date().toLocaleString('zh-CN')}`
      const canvas = document.createElement('canvas')
      canvas.width = 260
      canvas.height = 140
      const ctx = canvas.getContext('2d')
      ctx.rotate(-20 * Math.PI / 180)
      ctx.font = '12px sans-serif'
      ctx.fillStyle = 'rgba(120, 130, 140, 0.18)'
      ctx.fillText(text, 10, 90)
      this.watermarkImage = `url(${canvas.toDataURL('image/png')})`
    }
  }
}
</script>

<style scoped>
.pl-shell { height: 100%; }
.pl-aside { background: #1d2b36; }
.pl-logo { color: #fff; font-size: 18px; font-weight: 700; padding: 16px; letter-spacing: 1px; }
.pl-header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #e6e9ec; }
.pl-title { font-weight: 600; }
.pl-header-right { display: flex; align-items: center; gap: 18px; }
.pl-bell { cursor: pointer; font-size: 16px; }
.pl-user { cursor: pointer; }
.pl-main { background: #f5f7f9; }
</style>
