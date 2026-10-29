/**
 * v-permission 指令：按权限码控制元素显隐。
 * 注意（03 册 §1.2）：只控显隐，不作为唯一防线——服务端 @RequirePermission 才是。
 */
import store from '@/store'

export default {
  inserted(el, binding) {
    const code = binding.value
    if (code && !store.getters.hasPermission(code)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
