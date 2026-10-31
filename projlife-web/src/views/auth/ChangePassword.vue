<template>
  <!-- 修改密码（UID-02 §8.2）：首登强制改密为独立页（不渲染侧栏与顶栏），无"稍后再说/跳过" -->
  <div class="pl-changepwd">
    <el-card class="pl-changepwd-card" shadow="never">
      <div slot="header"><b>修改密码</b><span class="muted">至少 8 位，含字母与数字</span></div>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px" size="small">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="off"
                    @input="judgeStrength" />
          <div class="pl-strength" aria-live="polite">
            <span class="pl-strength-bar" :class="strengthClass"></span>
            <span class="pl-strength-text">{{ strengthText }}</span>
          </div>
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">确认修改</el-button>
          <el-button v-if="!forced" @click="$router.back()">返回</el-button>
        </el-form-item>
      </el-form>
      <p class="muted">内部系统，仅限授权人员使用；所有操作留痕审计（NFR-07/22）。</p>
    </el-card>
  </div>
</template>

<script>
import { showError } from '@/api/request'

export default {
  name: 'ChangePassword',
  data() {
    const confirmValidator = (rule, value, cb) => {
      if (value !== this.form.newPassword) cb(new Error('两次输入的新密码不一致'))
      else cb()
    }
    return {
      forced: this.$route.query.forced === '1',
      loading: false,
      form: { oldPassword: '', newPassword: '', confirm: '' },
      strengthClass: '',
      strengthText: '',
      rules: {
        oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
        newPassword: [
          { required: true, message: '请输入新密码', trigger: 'blur' },
          { min: 8, max: 64, message: '密码长度需在 8-64 位之间', trigger: 'blur' },
          {
            validator: (rule, value, cb) => {
              // ≥8 位含字母+数字（FR-SYS-02 R3）；强度提示只提示，弱但合法不阻断（UID-02 §8.2）
              if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) cb(new Error('须同时包含字母与数字'))
              else cb()
            },
            trigger: 'blur'
          }
        ],
        confirm: [{ required: true, validator: confirmValidator, trigger: 'blur' }]
      }
    }
  },
  methods: {
    judgeStrength(value) {
      let score = 0
      if (value.length >= 8) score++
      if (/[a-zA-Z]/.test(value) && /\d/.test(value)) score++
      if (/[^a-zA-Z0-9]/.test(value)) score++
      if (value.length >= 12) score++
      this.strengthClass = score <= 1 ? 'is-weak' : score === 2 ? 'is-mid' : 'is-strong'
      this.strengthText = value.length === 0 ? '' : score <= 1 ? '弱' : score === 2 ? '中' : '强'
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.loading = true
        this.$http.put('/auth/me/password', {
          oldPassword: this.form.oldPassword,
          newPassword: this.form.newPassword
        }).then(() => {
          this.$message.success('密码修改成功，请用新密码重新登录')
          this.$store.commit('SET_TOKEN', '')
          this.$router.push('/login')
        }).catch(showError).finally(() => { this.loading = false })
      })
    }
  }
}
</script>

<style scoped>
.pl-changepwd {
  height: 100%; display: flex; align-items: center; justify-content: center;
  background: var(--bg);
}
.pl-changepwd-card { width: 420px; border-radius: var(--r-3); }
.pl-strength { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.pl-strength-bar { height: 4px; width: 120px; border-radius: var(--r-1); background: var(--surface-3); position: relative; }
.pl-strength-bar::after { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 0; border-radius: var(--r-1); }
.is-weak.pl-strength-bar::after { width: 33%; background: var(--danger); }
.is-mid.pl-strength-bar::after { width: 66%; background: var(--warn); }
.is-strong.pl-strength-bar::after { width: 100%; background: var(--ok); }
.pl-strength-text { font-size: 12px; color: var(--ink-3); }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
