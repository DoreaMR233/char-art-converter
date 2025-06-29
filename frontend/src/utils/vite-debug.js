/**
 * @file Vite配置专用的调试工具模块。
 * @module utils/vite-debug
 */

import { loadEnv } from 'vite'

/**
 * Vite配置专用的条件调试日志函数。
 *
 * @function debugLog
 * @description 根据当前Node环境加载Vite环境变量，并且仅当 `VITE_DEBUG` 设置为 `'true'` 时，才向控制台输出调试信息。此函数主要用于Vite配置文件（如 `vite.config.js`）中。
 * @param {...any} args - 要输出到控制台的参数。
 */
export const debugLog = (...args) => {
  const env = loadEnv(process.env.NODE_ENV || 'development', process.cwd())
  if (env.VITE_DEBUG === 'true') {
    console.debug(...args)
  }
}

/**
 * 默认导出 `debugLog` 函数。
 *
 * @exports debugLog
 */
export default debugLog