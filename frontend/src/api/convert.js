/**
 * @file API通信模块，封装与后端服务器的基础HTTP请求。
 * @module api/convert
 */
import axios from 'axios'
import { debugLog } from '../utils/debug.js'

/**
 * @property {string} basePath - 基础路径，从环境变量 `VITE_BASE_PATH` 获取，默认为空字符串。
 * @property {string} apiBasePath - API基础路径，从环境变量 `VITE_API_BASE_PATH` 获取，默认为'/api'。
 * @property {string} apiFullBasePath - 完整的API基础路径，由 `basePath` 和 `apiBasePath` 拼接而成。
 */
// 基础路径，从环境变量获取，默认为空字符串
const basePath = import.meta.env.VITE_BASE_PATH || '';
// API基础路径，从环境变量获取，默认为'/api'
const apiBasePath = import.meta.env.VITE_API_BASE_PATH || '/api';
// 构建完整的API基础路径
const apiFullBasePath = basePath === '' ? apiBasePath : `/${basePath}${apiBasePath}`;
debugLog(`完整API基础路径:${apiFullBasePath}`)

/**
 * Axios HTTP客户端实例。
 *
 * @type {import('axios').AxiosInstance}
 * @description 该实例配置了API的基础URL和超时时间，并设置了请求和响应拦截器。
 */
const api = axios.create({
  baseURL: apiFullBasePath,
  timeout: 600000, // 10分钟超时时间，适配大文件处理需求
})

/**
 * HTTP请求拦截器。
 * @description 在请求发送前对请求配置进行预处理。
 */
api.interceptors.request.use(
    /**
   * 请求成功处理函数。
   *
   * @param {import('axios').AxiosRequestConfig} config - Axios请求配置对象。
   * @returns {import('axios').AxiosRequestConfig} 处理后的请求配置。
   */
  config => {
    return config
  },
    /**
   * 请求错误处理函数。
   *
   * @param {Error} error - 请求阶段发生的错误。
   * @returns {Promise<never>} 返回一个带有错误信息的Promise。
   */
  error => {
    return Promise.reject(error)
  }
)

/**
 * HTTP响应拦截器。
 * @description 在收到响应后对响应数据进行统一处理，特别处理Blob类型的错误响应。
 */
api.interceptors.response.use(
    /**
   * 响应成功处理函数。
   *
   * @param {import('axios').AxiosResponse} response - Axios响应对象。
   * @returns {import('axios').AxiosResponse} 处理后的响应对象。
   */
  response => {
    return response
  },
    /**
   * 响应错误处理函数。
   *
   * @description 特别处理Blob格式的错误响应，将其转换为可读的JSON格式。
   * @param {import('axios').AxiosError} error - Axios响应错误对象。
   * @returns {Promise<never>} 返回一个带有错误信息的Promise。
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
 * API接口方法定义。
 */

/**
 * 图片转字符画接口。
 *
 * @function convertImage
 * @description 上传图片文件并根据指定参数转换为字符画。
 * @param {FormData} formData - 包含图片和转换参数的表单数据。其中应包含 `image`（图片文件）、`density`（字符密度）、`colorMode`（颜色模式）、`limitSize`（是否限制尺寸）和 `progressId`（进度ID）。
 * @param {function(number): void} [onProgress] - 文件上传进度回调函数，接收一个0-100的进度百分比。
 * @returns {Promise<import('axios').AxiosResponse<any>>} 包含转换结果信息的响应对象。
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
 * 获取临时图片文件接口。
 *
 * @function getTempImage
 * @description 根据临时文件夹名称和文件名获取服务器上的临时图片文件。
 * @param {string} tempDirName - 临时文件夹名称。
 * @param {string} fileName - 文件名。
 * @param {string} contentType - 图片的内容类型。
 * @param {function} onDownloadProgress - 可选的下载进度回调函数。
 * @returns {Promise<import('axios').AxiosResponse<Blob>>} 包含图片Blob数据的响应对象。
 */
export const getTempImage = (tempDirName, fileName, contentType, onDownloadProgress) => {
  return api.get(`/get-temp-image/${tempDirName}/${fileName}`, {
    responseType: 'blob',
    headers: {
      'Accept': contentType || 'image/*'
    },
    onDownloadProgress: onDownloadProgress
  })
}



/**
 * 获取字符画文本内容接口。
 *
 * @function getCharText
 * @description 根据文件路径获取字符画的纯文本内容。
 * @param {string} filename - 字符画文件的相对路径。
 * @returns {Promise<import('axios').AxiosResponse<{find: boolean, text: string}>>} 包含字符画文本内容的响应对象，其中 `find` 表示是否找到文本，`text` 为文本内容。
 */
export const getCharText = (filename) => {
  return api.get('/text', {
    params: { filename }
  })
}



/**
 * 健康检查接口。
 *
 * @function checkHealth
 * @description 用于检测API服务是否正常运行。
 * @returns {Promise<import('axios').AxiosResponse<any>>} 包含健康状态的响应对象。
 */
export const checkHealth = () => {
  return api.get('/health')
}



/**
 * 默认导出配置好的API实例。
 *
 * @exports api
 */
export default api