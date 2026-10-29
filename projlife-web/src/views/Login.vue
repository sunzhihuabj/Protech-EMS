<template>
  <div class="pl-login">
    <div class="pl-login-card">
      <h2>ProjLife 企业级管理平台</h2>
      <p class="pl-sub">订单全生命周期 · 一体化经营总台</p>
      <el-form @submit.native.prevent="onSubmit">
        <el-form-item>
          <el-input v-model.trim="form.username" placeholder="登录名（工号/手机号）" prefix-icon="el-icon-user" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码"
                    prefix-icon="el-icon-lock" @keyup.enter.native="onSubmit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="pl-btn" native-type="submit">登 录</el-button>
      </el-form>
      <p class="pl-tip">连续错误 5 次将锁定 15 分钟；施工人员账号仅限微信小程序登录</p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LoginPage',
  data() {
    return {
      form: { username: '', password: '' },
      loading: false
    }
  },
  methods: {
    async onSubmit() {
      if (!this.form.username || !this.form.password) {
        this.$message.warning('请输入登录名与密码')
        return
      }
      this.loading = true
      try {
        const data = await this.$store.dispatch('login', this.form)
        if (data.mustChangePwd) {
          // 首登强制改密（S0-3）：改完自动重新登录
          await this.forceChangePassword(data.username)
          return
        }
        await this.afterLogin()
      } catch (e) {
        this.$showError(e)
      } finally {
        this.loading = false
      }
    },
    forceChangePassword() {
      return this.$prompt('首登必须修改密码（至少 8 位）', '强制改密', {
        inputType: 'password',
        inputPattern: /^.{8,64}$/,
        inputErrorMessage: '密码长度需在 8-64 位之间',
        closeOnClickModal: false,
        closeOnPressEscape: false,
        showCancelButton: false
      }).then(({ value }) => {
        return this.$http.put('/auth/me/password', { oldPassword: this.form.password, newPassword: value })
      }).then(() => {
        this.$message.success('密码修改成功，请用新密码登录')
        this.$store.commit('SET_TOKEN', '')
        this.form.password = ''
      }).catch(e => {
        if (e !== 'cancel') this.$showError(e)
      })
    },
    async afterLogin() {
      await this.$store.dispatch('fetchMe')
      const redirect = this.$route.query.redirect || '/'
      this.$router.push(redirect)
    }
  }
}
</script>

<style scoped>
.pl-login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #16323d 0%, #1f4a56 100%);
}
.pl-login-card {
  width: 380px;
  padding: 36px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.pl-login-card h2 { margin: 0 0 4px; }
.pl-sub { color: #8a97a3; margin: 0 0 24px; font-size: 13px; }
.pl-btn { width: 100%; }
.pl-tip { color: #98a5b1; font-size: 12px; margin-top: 16px; }
</style>
