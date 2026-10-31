<template>
  <!-- 登录页（UID-02 §8.1）：左品牌区（≥1366）+ 右表单区；错误不区分账号/密码（防枚举）；
       锁定显示"锁定至 HH:MM"；禁止自助注册与自助找回；审计提示常显 -->
  <div class="pl-login">
    <div class="pl-brand" aria-hidden="true">
      <div class="pl-brand-logo">P</div>
      <h1>Protech 企业综合业务管理系统</h1>
      <p>订单全生命周期 · 一体化经营总台</p>
    </div>
    <div class="pl-login-card">
      <h2 class="pl-login-title">登录</h2>
      <el-form @submit.native.prevent="onSubmit">
        <el-form-item>
          <el-input v-model.trim="form.username" placeholder="登录名（工号/手机号）" prefix-icon="el-icon-user"
                    aria-label="登录名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码"
                    prefix-icon="el-icon-lock" aria-label="密码" @keyup.enter.native="onSubmit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="pl-btn" native-type="submit">登 录</el-button>
      </el-form>
      <p v-if="lockUntilText" class="pl-lock" role="alert">{{ lockUntilText }}</p>
      <p class="pl-tip">连续错误 5 次将锁定 15 分钟；施工人员账号仅限微信小程序登录</p>
      <p class="pl-audit">内部系统，仅限授权人员使用；所有操作留痕审计（NFR-07/22）</p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LoginPage',
  data() {
    return {
      form: { username: '', password: '' },
      loading: false,
      lockUntilText: ''
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
          // 首登强制改密（FR-SYS-02 R3）：独立页、不渲染导航、无"稍后再说"（UID-02 §8.2）
          this.$router.push('/auth/change-password?forced=1')
          return
        }
        await this.$store.dispatch('fetchMe')
        this.$router.push(this.$route.query.redirect || '/workbench')
      } catch (e) {
        // 6001 锁定：显示"锁定至 HH:MM"（FR-SYS-16 R2）；错误不区分账号/密码（防枚举）
        if (e && e.code === 6001 && /(\d{1,2}:\d{2})/.test(e.message || '')) {
          this.lockUntilText = (e.message.match(/\d{1,2}:\d{2}/) || [])[0]
            ? `账号已锁定，锁定至 ${(e.message.match(/\d{1,2}:\d{2}/))[0]}`
            : '账号已锁定，请稍后再试'
        } else {
          this.$showError(e)
        }
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.pl-login {
  height: 100%; display: flex; align-items: center; justify-content: center; gap: 64px;
  background: linear-gradient(135deg, var(--pri-800) 0%, var(--pri-600) 100%);
}
.pl-brand { color: #fff; max-width: 380px; }
.pl-brand-logo {
  width: 48px; height: 48px; border-radius: var(--r-3); background: rgba(255,255,255,.12);
  display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 700;
  margin-bottom: 16px;
}
.pl-brand h1 { font-size: 24px; margin: 0 0 8px; }
.pl-brand p { color: rgba(255,255,255,.72); font-size: 14px; }
.pl-login-card {
  width: 380px; padding: 36px; background: var(--surface);
  border-radius: var(--r-3); border: 1px solid var(--line); box-shadow: var(--elev-3);
}
.pl-login-title { margin: 0 0 20px; font-size: 20px; color: var(--ink); }
.pl-btn { width: 100%; }
.pl-tip { color: var(--ink-3); font-size: 12px; margin-top: 16px; }
.pl-lock { color: var(--danger-ink); font-size: 13px; margin-top: 12px; }
.pl-audit { color: var(--ink-4); font-size: 11px; margin-top: 12px; border-top: 1px dashed var(--line); padding-top: 12px; }
@media (max-width: 1365px) { .pl-brand { display: none; } }
</style>
