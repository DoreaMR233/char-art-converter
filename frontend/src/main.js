/**
 * 应用程序入口文件
 * 初始化Vue应用并挂载到DOM
 */

// 导入Vue核心库和应用组件
import { createApp } from 'vue'
import App from './App.vue'

// 导入Element Plus UI库及其样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 导入全局样式
import './assets/main.css'

/**
 * 创建Vue应用实例
 * @constant {Object} app - Vue应用实例
 */
const app = createApp(App)

// 注册Element Plus插件
app.use(ElementPlus)

// 将应用挂载到DOM中的#app元素
app.mount('#app')