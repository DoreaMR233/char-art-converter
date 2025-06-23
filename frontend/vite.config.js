/**
 * Vite配置文件
 * 定义项目的构建和开发服务器配置
 */

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

/**
 * 环境变量配置
 * @param {Object} env - 环境变量对象
 * @param {string} mode - 当前模式（development/production）
 */
export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd())

  // 获取环境变量中的BASE_PATH，如果未设置则默认为空字符串
  const basePath = env.VITE_BASE_PATH || '';
  // 获取环境变量API_URL，如果未设置则为http://localhost:8080
  const apiUrl = env.VITE_API_URL || 'http://localhost:8080';
  // 获取API基础路径，如果未设置则为/api
  const apiBasePath = env.VITE_API_BASE_PATH || '/api';
  // 获取开发服务器端口
  const port = parseInt(env.VITE_PORT || '5174');
  // 是否启用源码映射
  const sourcemap = env.VITE_SOURCEMAP === 'true';
  // 生产模式下实际API基础路径：/资源路径前缀/API基础路径
  const actualApiBasePath =  basePath === '' ? apiBasePath : `/${basePath}${apiBasePath}`

  console.log(`当前模式: ${mode}`);
  console.log(`API URL: ${apiUrl}`);
  console.log(`API Base Path: ${apiBasePath}`);
  console.log(`Base Path: ${basePath}`);
  console.log(`Port: ${port}`);
  console.log(`Sourcemap: ${sourcemap}`);
  console.log(`actualApiBasePath: ${actualApiBasePath}`);

  return {

  /**
   * 插件配置
   * @property {Array} plugins - Vite插件列表
   */
  plugins: [
    vue(),                // 启用Vue 3支持
  ],
  base: basePath=== '' ? '/' : `/${basePath}/`,  // 设置资源基础路径

  /**
   * 开发服务器配置
   * @property {Object} server - 服务器选项
   */
  server: {
    port: port,
    /**
     * API代理配置
     * 将API基础路径开头的请求代理到后端服务器
     */
    proxy: {
      [apiBasePath]: {
        target: apiUrl, // 后端API服务器地址
        changeOrigin: true, // 修改请求头中的Host为目标URL
        ws: true,
        rewrite: (path) => path,

        /**
         * 代理配置函数
         * 自定义代理行为，特别是对SSE连接的处理
         * @param {Object} proxy - 代理实例
         * @param {Object} options - 代理选项
         */
        configure: (proxy, options) => {
          // 对于SSE连接，需要特殊处理
          proxy.on('error', (err, req, res) => {
            console.error('代理错误:', err);
            // 尝试向客户端发送错误信息
            if (!res.headersSent && res.writeHead) {
              res.writeHead(500, {
                'Content-Type': 'application/json'
              });
              res.end(JSON.stringify({ error: 'Proxy Error', message: err.message }));
            }
          });

          proxy.on('proxyReq', (proxyReq, req, res) => {
            console.log('代理请求:', req.url, '方法:', req.method);
            // 对于SSE请求，添加特殊处理
            if (req.url.includes('/progress/')) {
              console.log('检测到SSE请求，添加特殊处理');
              // 确保不缓存SSE响应
              proxyReq.setHeader('Cache-Control', 'no-cache, no-transform');
              proxyReq.setHeader('Connection', 'keep-alive');
              proxyReq.setHeader('Accept', 'text/event-stream');
            }
          });

          // 添加响应拦截器，处理CORS和SSE相关的头部
          proxy.on('proxyRes', (proxyRes, req, res) => {
            console.log('代理响应:', req.url, '状态码:', proxyRes.statusCode);

            if (req.url.includes('/progress/')) {
              console.log('处理SSE响应头');
              // SSE 相关头部
              proxyRes.headers['Cache-Control'] = 'no-cache, no-transform';
              proxyRes.headers['Connection'] = 'keep-alive';
              proxyRes.headers['Content-Type'] = 'text/event-stream';
              proxyRes.headers['X-Accel-Buffering'] = 'no';

              // CORS 相关头部
              proxyRes.headers['Access-Control-Allow-Origin'] = req.headers.origin || '*';
              proxyRes.headers['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS';
              proxyRes.headers['Access-Control-Allow-Headers'] = 'Content-Type, Authorization';

              // 确保响应不被压缩，这可能会导致SSE连接问题
              delete proxyRes.headers['content-encoding'];

              // 记录响应头信息，用于调试
              console.log('SSE响应头:', JSON.stringify(proxyRes.headers));
            }
          });
        }
      }
    }
  },

  /**
   * 路径解析配置
   * @property {Object} resolve - 路径解析选项
   */
  resolve: {
    alias: {
      /**
       * 路径别名配置
       * 将@映射到src目录，方便导入
       */
      '@': path.resolve(__dirname, 'src')
    }
  },

  /**
   * 构建配置
   * @property {Object} build - 构建选项
   */
  build: {
    /**
     * 源码映射配置
     * 在生产环境中可以通过环境变量控制是否生成源码映射
     */
    sourcemap: sourcemap
  }
  }
})