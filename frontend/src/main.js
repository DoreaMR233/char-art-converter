/**
 * @file main.js
 * @description Vue应用程序的入口文件。负责创建Vue应用实例、配置插件（如Element Plus）、设置全局样式、处理环境变量，并最终将应用挂载到DOM上。
 */

import { createApp } from 'vue'
import App from './App.vue'
import { debugLog } from './utils/debug.js'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import './assets/main.css'

// 在开发模式下，如果VITE_DEBUG为true，则打印环境变量以供调试。
debugLog('当前环境:', import.meta.env.MODE)
debugLog('API前缀:', import.meta.env.VITE_API_URL)
debugLog('资源路径前缀:', import.meta.env.VITE_BASE_PATH)
const apiFullBasePath = import.meta.env.VITE_BASE_PATH === '' ? import.meta.env.VITE_API_URL : `/${import.meta.env.VITE_BASE_PATH}${import.meta.env.VITE_API_URL}`;
debugLog(`完整API基础路径:${apiFullBasePath}`)
debugLog('应用标题:', import.meta.env.VITE_APP_TITLE)
debugLog('应用版本:', import.meta.env.VITE_APP_VERSION)

/**
 * 创建Vue应用实例。
 * @type {import('vue').App}
 */
const app = createApp(App)

// 全局注册Element Plus插件。
app.use(ElementPlus)

/**
 * @description 从环境变量构建API基础路径，并将其提供给所有子组件。
 */
const basePath = import.meta.env.VITE_BASE_PATH || '';
const apiBasePath = import.meta.env.VITE_API_BASE_PATH || '/api';
const actualApiBasePath = basePath === '' ? apiBasePath : `/${basePath}${apiBasePath}`;

// 将Vue应用挂载到DOM中的#app元素。
app.mount('#app')

// 通过依赖注入提供API基础路径，使其在所有子组件中可用。
app.provide('actualApiBasePath', actualApiBasePath)
