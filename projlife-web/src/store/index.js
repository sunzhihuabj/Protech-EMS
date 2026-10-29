/**
 * 全局状态：登录用户、权限、菜单、主题（S0-14/S0-17）。
 */
import Vue from 'vue'
import Vuex from 'vuex'
import router from '@/router'
import http from '@/api/request'
import { applyTheme, resetLocalTheme } from '@/theme/theme'

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    token: localStorage.getItem('pl_token') || '',
    user: null,
    permissions: [],
    menus: [],
    defaultTheme: 't1',
    unread: 0
  },
  getters: {
    isLoggedIn: s => !!s.token,
    hasPermission: s => code => s.permissions.includes(code)
  },
  mutations: {
    SET_TOKEN(state, token) {
      state.token = token
      if (token) {
        localStorage.setItem('pl_token', token)
      } else {
        localStorage.removeItem('pl_token')
      }
    },
    SET_ME(state, me) {
      state.user = me
      state.permissions = me.permissions || []
      state.menus = me.menus || []
      state.defaultTheme = me.defaultTheme || 't1'
    },
    SET_UNREAD(state, count) {
      state.unread = count || 0
    },
    SET_DEFAULT_THEME(state, theme) {
      state.defaultTheme = theme
    }
  },
  actions: {
    async login({ commit }, form) {
      const body = await http.post('/auth/login', form)
      const data = body.data
      commit('SET_TOKEN', data.accessToken)
      localStorage.setItem('pl_refresh_token', data.refreshToken)
      return data
    },
    async fetchMe({ commit, state }) {
      const body = await http.get('/auth/me')
      commit('SET_ME', body.data)
      // 有效主题 = 个人偏好 → 系统默认（03 册 §6A.1 覆盖规则）
      const effective = (state.user && state.user.themeCode) || state.defaultTheme
      applyTheme(effective, false)
      try {
        const unread = await http.get('/notifications/summary')
        commit('SET_UNREAD', (unread.data && unread.data.unread) || 0)
      } catch (e) { /* 未读数失败不阻塞 */ }
      return body.data
    },
    async saveTheme({ dispatch }, themeCode) {
      await http.put('/auth/me/theme', { themeCode: themeCode || '' })
      await dispatch('fetchMe')
    },
    resetTheme({ state, dispatch }) {
      resetLocalTheme(state.defaultTheme)
      return dispatch('saveTheme', '')
    },
    async logout({ commit }) {
      try {
        await http.post('/auth/logout')
      } catch (e) { /* 已失效也继续清理 */ }
      commit('SET_TOKEN', '')
      router.push('/login')
    },
    forceLogout({ commit }) {
      commit('SET_TOKEN', '')
      if (router.currentRoute.name !== 'login') {
        router.push('/login')
      }
    }
  }
})
