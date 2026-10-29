/**
 * Web 前端入口（S0-14）。
 * Element UI 全量引入（内网部署，无 CDN 依赖；15 册性能口径：按需引入在 S8 优化批次处理）。
 */
import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import '@/styles/base.css'
import App from '@/App.vue'
import router from '@/router'
import store from '@/store'
import permission from '@/directive/permission'
import { applyTheme } from '@/theme/theme'
import http, { showError } from '@/api/request'

Vue.use(ElementUI, { size: 'small' })
// 全局挂载：组件内 this.$http / this.$showError
Vue.prototype.$http = http
Vue.prototype.$showError = showError
Vue.directive('permission', permission)
Vue.config.productionTip = false

// 首帧主题：本地缓存先行（index.html 已防闪烁，此处对齐运行时状态）
applyTheme(document.documentElement.getAttribute('data-theme') || 't1', false)

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')
