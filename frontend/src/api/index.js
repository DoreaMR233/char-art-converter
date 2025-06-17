/**
 * API模块 - 处理与后端服务器的所有通信
 * 包含图片转换、文本获取和进度监控的API调用
 */
import axios from 'axios'

/**
 * 创建axios实例，配置基本参数
 * @constant {Object} api - 配置好的axios实例
 */
const api = axios.create({
  baseURL: '/api',
  timeout: 60000, // 较长的超时时间，因为图片处理可能需要时间
})

/**
 * 请求拦截器 - 在请求发送前处理请求配置
 */
api.interceptors.request.use(
  /**
   * 请求成功拦截函数
   * @param {Object} config - 请求配置对象
   * @returns {Object} 处理后的请求配置
   */
  config => {
    return config
  },
  /**
   * 请求错误拦截函数
   * @param {Error} error - 请求错误对象
   * @returns {Promise} 返回带有错误信息的Promise
   */
  error => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器 - 在收到响应后处理响应数据
 */
api.interceptors.response.use(
  /**
   * 响应成功拦截函数
   * @param {Object} response - 响应对象
   * @returns {Object} 处理后的响应对象
   */
  response => {
    return response
  },
  /**
   * 响应错误拦截函数
   * @param {Error} error - 响应错误对象
   * @returns {Promise} 返回带有错误信息的Promise
   */
  error => {
    console.error('API请求错误:', error)
    return Promise.reject(error)
  }
)

/**
 * API方法
 */

/**
 * 将图片转换为字符画
 * @function convertImage
 * @param {FormData} formData - 包含图片文件和转换参数的表单数据
 *   @param {File} formData.image - 要转换的图片文件
 *   @param {string} formData.density - 字符密度 ('low', 'medium', 'high')
 *   @param {string} formData.colorMode - 颜色模式 ('color', 'grayscale')
 *   @param {boolean} formData.limitSize - 是否限制输出尺寸
 *   @param {string} formData.progressId - 进度追踪ID
 * @param {Function} onProgress - 上传进度回调函数，接收百分比参数
 * @returns {Promise<Object>} 返回包含字符画图片的响应对象，数据为Blob格式
 */
export const convertImage = (formData, onProgress) => {
  return api.post('/convert', formData, {
    timeout: 600000, // 10分钟超时
    responseType: 'blob',
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
 * 获取字符画的文本内容
 * @function getCharText
 * @param {string} filename - 原始图片的文件名，用于在服务器上查找对应的字符画文本
 * @returns {Promise<Object>} 返回包含字符画文本的响应对象
 *   @returns {boolean} response.data.find - 是否找到字符画文本
 *   @returns {string} response.data.text - 字符画文本内容（如果找到）
 */
export const getCharText = (filename) => {
  return api.get('/text', {
    params: { filename }
  })
}

/**
 * 订阅服务器发送事件(SSE)获取处理进度
 * @function subscribeToProgress
 * @param {string} id - 进度追踪ID，与转换请求中的progressId对应
 * @param {Function} onMessage - 消息处理回调函数，接收进度数据对象
 *   @param {number} data.percentage - 处理进度百分比
 *   @param {string} data.stage - 当前处理阶段
 *   @param {string} data.message - 进度消息
 *   @param {number} data.currentPixel - 当前处理的像素数
 *   @param {number} data.totalPixels - 总像素数
 *   @param {string} data.connectionStatus - 连接状态 ('connecting', 'reconnecting', 'failed', 'error')
 * @returns {EventSource} 返回创建的EventSource实例，可用于关闭连接
 */
export const subscribeToProgress = (id, onMessage) => {
  let eventSource = new EventSource(`/api/progress/${id}`)
  
  /**
   * 连接打开时的处理
   * @event onopen
   */
  eventSource.onopen = () => {
    console.log('EventSource连接已打开')
    console.log('EventSource readyState:', eventSource.readyState)
  }

  /**
   * 处理心跳消息 - 保持连接活跃
   * @event heartbeat
   * @param {Event} event - 事件对象
   */
  eventSource.addEventListener("heartbeat",(event) => {
    console.log('收到心跳消息')
  })

  /**
   * 处理初始化消息 - 确认连接建立
   * @event init
   * @param {Event} event - 事件对象，包含初始化数据
   */
  eventSource.addEventListener("init",(event) => {
    console.log('已接收到后端消息：',event.data)
  })
  /**
   * 处理进度消息 - 接收处理进度更新
   * @event progress
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
        if (data.percentage >= 100) {
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
   * 错误处理配置
   */
  let retryCount = 0
  const maxRetries = 3
  
  /**
   * 处理连接错误
   * @event onerror
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
 * 检查后端服务健康状态
 * @function checkHealth
 * @returns {Promise<Object>} 返回包含服务健康状态的响应对象
 *   @returns {string} response.data.status - 服务状态，正常时为"UP"
 *   @returns {string} response.data.message - 状态描述信息
 */
export const checkHealth = () => {
  return api.get('/health')
}

/**
 * 导出配置好的API实例作为默认导出
 * @exports api
 */
export default api