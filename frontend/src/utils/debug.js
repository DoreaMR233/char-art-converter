/**
 * @file debug.js
 * @description 调试工具模块，提供统一的调试日志功能
 * @module utils/debug
 */

/**
 * @function debugLog
 * @description 条件调试日志函数，只在开发模式且VITE_DEBUG为true时输出
 * @param {...any} args - 要输出的参数
 */
export const debugLog = (...args) => {
  if (import.meta.env.DEV && import.meta.env.VITE_DEBUG === 'true') {
    console.debug(...args)
  }
}

/**
 * 导出 debugLog 作为默认导出
 * @exports debugLog
 */
export default debugLog