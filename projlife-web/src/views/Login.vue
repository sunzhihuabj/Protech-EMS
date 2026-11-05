<template>
  <!-- 登录页（UID-02 §8.1）：左品牌区（≥1366）+ 右表单区；错误不区分账号/密码（防枚举）；
       锁定显示"锁定至 HH:MM"；禁止自助注册与自助找回；审计提示常显 -->
  <div class="pl-login">
    <!-- 背景装饰层：工程网格 + 主色 glow（纯 CSS，随主题主色联动） -->
    <div class="pl-login-bg" aria-hidden="true">
      <div class="pl-login-grid"></div>
      <div class="pl-login-glow pl-login-glow--a"></div>
      <div class="pl-login-glow pl-login-glow--b"></div>
    </div>

    <div class="pl-brand" aria-hidden="true">
      <div class="pl-brand-logo">
        <svg viewBox="0 0 48 48" width="44" height="44">
          <path d="M12 40 V10 h14 a10 10 0 0 1 0 20 h-14" fill="none"
                stroke="currentColor" stroke-width="4" stroke-linecap="square" />
          <path d="M26 30 v10" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="square" />
        </svg>
      </div>
      <h1>Protech 企业综合业务管理系统</h1>
      <p class="pl-brand-sub">订单全生命周期 · 一体化经营总台</p>
      <ul class="pl-brand-points">
        <li><i class="el-icon-circle-check"></i>商机 → 合同 → 交付 → 回款 全链闭环</li>
        <li><i class="el-icon-circle-check"></i>九板经营驾驶舱 · 实时红灯预警</li>
        <li><i class="el-icon-circle-check"></i>全程留痕审计 · 数据范围分级管控</li>
      </ul>
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
  height: 100%; position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: center; gap: 72px;
  background: linear-gradient(135deg, var(--pri-900) 0%, var(--pri-700) 55%, var(--pri-600) 100%);
}

/* --- 背景装饰：工程网格 + 双 glow 光斑（取主色，随主题联动） --- */
.pl-login-bg { position: absolute; inset: 0; pointer-events: none; }
.pl-login-grid {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 90% 80% at 50% 45%, #000 30%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 90% 80% at 50% 45%, #000 30%, transparent 100%);
}
.pl-login-glow { position: absolute; border-radius: 50%; filter: blur(90px); opacity: .35; }
.pl-login-glow--a { width: 480px; height: 480px; left: -120px; bottom: -160px; background: var(--pri-300); }
.pl-login-glow--b { width: 360px; height: 360px; right: -100px; top: -120px; background: var(--accent); opacity: .22; }

/* --- 品牌区 --- */
.pl-brand { position: relative; color: #fff; max-width: 400px; animation: pl-rise .5s ease both; }
.pl-brand-logo {
  width: 56px; height: 56px; border-radius: var(--r-3);
  background: rgba(255,255,255,.10); border: 1px solid rgba(255,255,255,.18);
  display: flex; align-items: center; justify-content: center; color: var(--accent);
  margin-bottom: 20px; box-shadow: inset 0 1px 0 rgba(255,255,255,.12);
}
.pl-brand h1 { font-size: 26px; margin: 0 0 10px; letter-spacing: 1px; }
.pl-brand-sub { color: rgba(255,255,255,.72); font-size: 14px; margin: 0 0 28px; }
.pl-brand-points { list-style: none; margin: 0; padding: 0; }
.pl-brand-points li {
  display: flex; align-items: center; gap: 8px; font-size: 13px;
  color: rgba(255,255,255,.78); padding: 7px 0;
}
.pl-brand-points i { color: var(--accent); font-size: 14px; }

/* --- 表单卡（入场动效 + 细节精修） --- */
.pl-login-card {
  position: relative; width: 380px; padding: 38px 36px 30px; background: var(--surface);
  border-radius: var(--r-4); border: 1px solid var(--line); box-shadow: var(--elev-3);
  animation: pl-rise .5s ease .12s both;
}
.pl-login-card::before {
  content: ''; position: absolute; top: 0; left: 36px; right: 36px; height: 3px;
  border-radius: 0 0 var(--r-1) var(--r-1); background: var(--pri-500);
}
.pl-login-title { margin: 0 0 22px; font-size: 20px; color: var(--ink); }
.pl-btn { width: 100%; letter-spacing: 6px; font-weight: 600; }
.pl-tip { color: var(--ink-3); font-size: 12px; margin-top: 16px; }
.pl-lock { color: var(--danger-ink); font-size: 13px; margin-top: 12px; }
.pl-audit { color: var(--ink-4); font-size: 11px; margin-top: 12px; border-top: 1px dashed var(--line); padding-top: 12px; }

@keyframes pl-rise {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
  .pl-brand, .pl-login-card { animation: none; }
}
@media (max-width: 1365px) { .pl-brand { display: none; } }
</style>
