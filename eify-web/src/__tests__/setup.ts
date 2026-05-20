import { config } from '@vue/test-utils'

// 全局 stubs：避免测试中加载真实的重依赖组件
config.global.stubs = {
  Transition: false,
  ElMessage: true,
  ElMessageBox: true
}
