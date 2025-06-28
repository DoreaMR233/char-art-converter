/**
 * @file convert.js
 * @description API通信模块，封装与后端服务器的基础HTTP请求
 * @module api
 */
import axios from 'axios'
import { debugLog } from '../utils/debug.js'

/**
 * @description 环境变量配置
 */
// 基础路径，从环境变量获取，默认为空字符串
const basePath = import.meta.env.VITE_BASE_PATH || '';
// API基础路径，从环境变量获取，默认为'/api'
const apiBasePath = import.meta.env.VITE_API_BASE_PATH || '/api';
// 构建完整的API基础路径
const apiFullBasePath = basePath === '' ? apiBasePath : `/${basePath}${apiBasePath}`;
debugLog(`完整API基础路径:${apiFullBasePath}`)

/**
 * @constant {AxiosInstance} api
 * @description 创建配置好的axios HTTP客户端实例
 */
const api = axios.create({
  baseURL: apiFullBasePath,
  timeout: 600000, // 10分钟超时时间，适配大文件处理需求
})

/**
 * @description HTTP请求拦截器，在请求发送前对请求配置进行预处理
 */
api.interceptors.request.use(
  /**
   * @function requestSuccessHandler
   * @param {Object} config - axios请求配置对象
   * @returns {Object} 处理后的请求配置
   */
  config => {
    return config
  },
  /**
   * @function requestErrorHandler
   * @param {Error} error - 请求阶段发生的错误
   * @returns {Promise<never>} 拒绝状态的Promise
   */
  error => {
    return Promise.reject(error)
  }
)

/**
 * @description HTTP响应拦截器，在收到响应后对响应数据进行统一处理，特别处理Blob类型的错误响应
 */
api.interceptors.response.use(
  /**
   * @function responseSuccessHandler
   * @param {Object} response - axios响应对象
   * @returns {Object} 处理后的响应对象
   */
  response => {
    return response
  },
  /**
   * @function responseErrorHandler
   * @description 特别处理Blob格式的错误响应，将其转换为可读的JSON格式
   * @param {Error} error - axios响应错误对象
   * @returns {Promise<never>} 拒绝状态的Promise
   */
  async error => {
    // 检查响应数据是否为JSON格式的Blob类型
    if (error.response && error.response.data instanceof Blob && 
        error.response.data.type === 'application/json') {
      // 将Blob格式的错误响应转换为可读的JSON对象
      try {
        const jsonText = await error.response.data.text();
        // 解析JSON文本为对象
        const errorData = JSON.parse(jsonText);
        // 更新错误对象中的响应数据
        error.response.data = errorData;
        console.error('API请求错误:', errorData);
      } catch (e) {
        console.error('解析Blob错误响应失败:', e);
        console.error('原始错误响应数据:', error.response.data);
      }
    } else {
      console.error('API请求错误:', error.response ? error.response.data : error.message);
    }
    return Promise.reject(error)
  }
)

/**
 * @description API接口方法定义
 */

/**
 * @function convertImage
 * @description 图片转字符画接口，上传图片文件并根据指定参数转换为字符画
 * @param {FormData} formData - 包含图片和转换参数的表单数据
 * @param {File} formData.image - 待转换的图片文件
 * @param {string} formData.density - 字符密度等级 ('low'|'medium'|'high')
 * @param {string} formData.colorMode - 颜色模式 ('color'|'grayscale')
 * @param {boolean} formData.limitSize - 是否限制输出图片尺寸
 * @param {string} formData.progressId - 用于进度跟踪的唯一标识符
 * @param {Function} [onProgress] - 文件上传进度回调函数
 * @param {number} onProgress.percentage - 上传进度百分比(0-100)
 * @returns {Promise<Object>} 包含转换结果信息的响应对象
 */
export const convertImage = (formData, onProgress) => {
  return api.post('/convert', formData, {
    timeout: 600000, // 10分钟超时
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress: progressEvent => {
      if (onProgress && progressEvent.total) {
        const percentage = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        onProgress(percentage)
      }
    }
  })
}

/**
 * @function getTempImage
 * @description 获取临时图片文件接口，根据文件路径获取服务器上的临时图片文件
 * @param {string} filePath - 临时图片文件的相对路径
 * @param {string} contentType - 图片的内容类型
 * @returns {Promise<Object>} 包含图片Blob数据的响应对象
 */
export const getTempImage = (filePath, contentType) => {
  return api.get(`/get-temp-image/${filePath}`, {
    responseType: 'blob',
    headers: {
      'Accept': contentType || 'image/*'
    }
  })
}



/**
 * @function getCharText
 * @description 获取字符画文本内容接口，根据文件路径获取字符画的纯文本内容
 * @param {string} filename - 字符画文件的相对路径
 * @returns {Promise<Object>} 包含字符画文本内容的响应对象
 * @returns {boolean} response.data.find - 是否找到字符画文本
 * @returns {string} response.data.text - 字符画文本内容（如果找到）
 */
export const getCharText = (filename) => {
  return api.get('/text', {
    params: { filename }
  })
}



/**
 * @function checkHealth
 * @description 健康检查接口，用于检测API服务是否正常运行
 * @returns {Promise<Object>} 包含健康状态的响应对象
 */
export const checkHealth = () => {
  return api.get('/health')
}



/**
 * 导出配置好的API实例作为默认导出
 * @exports api
 */
export default api