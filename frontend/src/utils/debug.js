/**
 * @file 调试工具模块，提供统一的调试日志功能。
 * @module utils/debug
 */

/**
 * 条件调试日志函数。
 *
 * @function debugLog
 * @description 只在Vite开发模式 (`import.meta.env.DEV` 为 `true`) 且环境变量 `VITE_DEBUG` 设置为 `'true'` 时，才向控制台输出调试信息。
 * @param {...any} args - 要输出到控制台的参数。
 */
export const debugLog = (...args) => {
  if (import.meta.env.DEV && import.meta.env.VITE_DEBUG === 'true') {
    console.debug(...args)
  }
}

/**
 * 默认导出 `debugLog` 函数。
 *
 * @exports debugLog
 */
export default debugLog