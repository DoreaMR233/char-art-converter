/**
 * @file index.js
 * @description API通信模块，封装与后端服务器的所有HTTP请求和SSE连接
 * @module api
 */
import axios from 'axios'

/**
 * @description 环境变量配置
 */
// 基础路径，从环境变量获取，默认为空字符串
const basePath = import.meta.env.VITE_BASE_PATH || '';
// API基础路径，从环境变量获取，默认为'/api'
const apiBasePath = import.meta.env.VITE_API_BASE_PATH || '/api';
// 构建完整的API基础路径
const apiFullBasePath = basePath === '' ? apiBasePath : `/${basePath}${apiBasePath}`;
console.log(`完整API基础路径:${apiFullBasePath}`)

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
 * @function subscribeToProgress
 * @description 订阅转换进度接口，通过Server-Sent Events(SSE)实时获取图片转换进度
 * @param {string} id - 用于进度跟踪的唯一标识符
 * @param {Function} onMessage - 消息回调函数
 * @returns {EventSource} 事件源对象，可用于取消订阅
 */
export const subscribeToProgress = (id, onMessage) => {
  let eventSource = new EventSource(`${apiFullBasePath}/progress/${id}`)
  
  /**
   * @event onopen
   * @description 连接打开时的处理
   */
  eventSource.onopen = () => {
    console.log('EventSource连接已打开')
    console.log('EventSource readyState:', eventSource.readyState)
  }

  /**
   * @event heartbeat
   * @description 处理心跳消息 - 保持连接活跃
   * @param {Event} event - 事件对象
   */
  eventSource.addEventListener("heartbeat",(event) => {
    console.log('收到心跳消息',event.data)
  })

  /**
   * @event init
   * @description 处理初始化消息 - 确认连接建立
   * @param {Event} event - 事件对象，包含初始化数据
   */
  eventSource.addEventListener("init",(event) => {
    console.log('已接收到后端消息：',event.data)
  })
  
  /**
   * @event close
   * @description 处理关闭消息 - 接收服务器发送的关闭连接指令
   * @param {Event} event - 事件对象，包含关闭消息
   */
  eventSource.addEventListener("close",(event) => {
    console.log('收到关闭连接消息:', event.data)
    if (eventSource && eventSource.readyState !== 2) { // 如果连接未关闭
      console.log('关闭EventSource连接')
      eventSource.close()
      
      // 通知UI连接已关闭
      onMessage({
        connectionStatus: 'closed',
        stage: '错误',
        message: '由于服务器长时间为响应，已关闭消息通知连接',
        percentage: 100,
        done: true
      })
    }
  })

  /**
   * @event progress
   * @description 处理进度消息 - 接收处理进度更新
   * @param {Event} event - 事件对象，包含进度数据
   */
  eventSource.addEventListener("progress",(event) => {
    try {
      const data = JSON.parse(event.data)
      console.log('解析后的数据:', data)
      
      // 确保数据中包含必要的字段
      if (data && typeof data === 'object') {
        // 确保percentage字段存在且为数字
        if (data.percentage !== undefined) {
          data.percentage = Number(data.percentage)
        }
        
        // 确保currentPixel和totalPixels字段存在且为数字
        if (data.currentPixel !== undefined) {
          data.currentPixel = Number(data.currentPixel)
        }
        
        if (data.totalPixels !== undefined) {
          data.totalPixels = Number(data.totalPixels)
        }
        
        // 调用回调函数传递数据
        onMessage(data)
        
        // 如果进度达到100%，关闭连接
        if (data.done) {
          console.log('进度完成，关闭EventSource')
          eventSource.close()
        }

        // 重置请求超时时间
        api.defaults.timeout = 600000; // 重置为10分钟
        console.log('请求超时时间已重置')
      } else {
        console.error('收到无效的进度数据:', data)
      }
    } catch (error) {
      console.error('解析消息数据失败:', error, '原始数据:', event.data)
      // 不要在解析错误时关闭连接，可能只是单条消息有问题
    }
  })

  /**
   * @description 错误处理配置 - 定义重试次数和最大重试限制
   */
  let retryCount = 0
  const maxRetries = 3
  
  /**
   * @event onerror
   * @description 处理连接错误 - 连接错误或中断时的处理
   * @param {Event} error - 错误事件对象
   */
  eventSource.onerror = function(error) {
    console.error('EventSource连接错误:', error)
    console.log('EventSource readyState:', eventSource.readyState)
    console.log('EventSource URL:', eventSource.url)
    console.log('错误详情:', {
      type: error.type,
      target: error.target,
      timeStamp: error.timeStamp
    })
    
    if (eventSource.readyState === 2) { // CLOSED
      console.log('连接已关闭，尝试重连...')
      
      if (retryCount < maxRetries) {
        retryCount++
        console.log(`第${retryCount}次重连尝试...`)
        
        // 通知UI显示重连状态
        onMessage({
          connectionStatus: 'reconnecting',
          message: `正在重连... (${retryCount}/${maxRetries})`
        })
        
        setTimeout(() => {
          // 关闭当前连接
          eventSource.close()
          // 创建新的连接
          const newEventSource = subscribeToProgress(id, onMessage)
          // 更新引用
          Object.setPrototypeOf(eventSource, Object.getPrototypeOf(newEventSource))
          Object.assign(eventSource, newEventSource)
        }, 2000) // 增加重连间隔到2秒
      } else {
        console.error('重连次数已达上限，停止重连')
        eventSource.close()
        onMessage({
          connectionStatus: 'failed',
          message: '连接失败，请检查后端服务是否正常运行'
        })
      }
    } else if (eventSource.readyState === 0) { // CONNECTING
      console.log('连接正在建立中...')
      onMessage({
        connectionStatus: 'connecting'
      })
    }
  }

  return eventSource
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
 * @function getWebpProgressUrl
 * @description 获取WebP进度图片URL，用于显示转换进度的WebP动态图片URL
 * @param {string} id - 进度标识符
 * @returns {string} WebP图片的完整URL
 */
export const getWebpProgressUrl = (id) => {
  return `${apiFullBasePath}/progress-image/${id}.webp`
}

/**
 * @function closeProgress
 * @description 关闭进度连接，通知服务器关闭指定ID的进度连接
 * @param {string} id - 进度连接的唯一标识符
 * @returns {Promise<Object>} 关闭操作的响应对象
 */
export const closeProgress = (id) => {
  return api.post(`/close-progress/${id}`)
}

/**
 * 导出配置好的API实例作为默认导出
 * @exports api
 */
export default api