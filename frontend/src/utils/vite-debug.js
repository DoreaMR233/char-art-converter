/**
 * @file vite-debug.js
 * @description Vite配置专用的调试工具模块
 * @module utils/vite-debug
 */

import { loadEnv } from 'vite'

/**
 * @function debugLog
 * @description Vite配置专用的条件调试日志函数
 * @param {...any} args - 要输出的参数
 */
export const debugLog = (...args) => {
  const env = loadEnv(process.env.NODE_ENV || 'development', process.cwd())
  if (env.VITE_DEBUG === 'true') {
    console.debug(...args)
  }
}

/**
 * 导出 debugLog 作为默认导出
 * @exports debugLog
 */
export default debugLog