/**
 * Vue应用程序入口文件
 * 负责创建Vue应用实例、配置插件、设置全局配置并挂载到DOM
 */

// 导入Vue 3核心库和根组件
import { createApp } from 'vue'
import App from './App.vue'

// 导入Element Plus UI组件库及其CSS样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 导入应用全局样式文件
import './assets/main.css'

// 开发环境下输出环境变量信息用于调试
if (import.meta.env.DEV) {
  console.log('当前环境:', import.meta.env.MODE)
  console.log('API前缀:', import.meta.env.VITE_API_URL)
  console.log('资源路径前缀:', import.meta.env.VITE_BASE_PATH)
  const apiFullBasePath = import.meta.env.VITE_BASE_PATH === '' ? import.meta.env.VITE_API_URL : `/${import.meta.env.VITE_BASE_PATH}${import.meta.env.VITE_API_URL}`;
  console.log(`完整API基础路径:${apiFullBasePath}`)
  console.log('应用标题:', import.meta.env.VITE_APP_TITLE)
  console.log('应用版本:', import.meta.env.VITE_APP_VERSION)
}

/**
 * 创建Vue 3应用实例
 * @type {App} Vue应用实例对象
 */
const app = createApp(App)

// 全局注册Element Plus UI组件库
app.use(ElementPlus)

// 从环境变量构建API基础路径配置
const basePath = import.meta.env.VITE_BASE_PATH || '';
const apiBasePath = import.meta.env.VITE_API_BASE_PATH || '/api';
const actualApiBasePath = basePath === '' ? apiBasePath : `/${basePath}${apiBasePath}`;

// 将Vue应用挂载到DOM中的#app元素
app.mount('#app')
// 通过依赖注入提供API基础路径给所有子组件使用
app.provide('actualApiBasePath', actualApiBasePath)
