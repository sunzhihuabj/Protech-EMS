/**
 * axios 统一封装：token 注入 / 401 跳登录 / 422 硬卡点弹窗（16 册 §4.3 响应契约）。
 */
import axios from 'axios'
import { Message, MessageBox } from 'element-ui'
import store from '@/store'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000
})

http.interceptors.request.use(config => {
  const token = localStorage.getItem('pl_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 端标识（BR-08 端限制判定）：Web 端固定 web
  config.headers['X-Client'] = 'web'
  return config
})

http.interceptors.response.use(
  response => {
    const body = response.data
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      return Promise.reject(buildError(body))
    }
    return body
  },
  error => {
    const body = error.response && error.response.data
    if (error.response && error.response.status === 401) {
      store.dispatch('forceLogout')
      Message.error((body && body.message) || '未登录或会话过期')
      return Promise.reject(buildError(body || { code: 2001, message: '未登录' }))
    }
    return Promise.reject(buildError(body || { code: 9001, message: '网络异常' }))
  }
)

function buildError(body) {
  const err = new Error(body.message || '请求失败')
  err.code = body.code
  err.data = body.data || {}
  return err
}

/** 统一错误提示：硬卡点 422 弹窗给引导语，其余轻提示 */
export function showError(err) {
  if (err && err.code === 5001) {
    const block = err.data || {}
    MessageBox.alert(
      `${block.suggestion || '请联系管理员处理相关限制'}`,
      `操作被阻断（${block.blockCode || 'BR'}）`,
      { confirmButtonText: '知道了', type: 'warning' }
    )
    return
  }
  Message.error((err && err.message) || '操作失败')
}

export default http
