/**
 * @file 进度管理API模块，处理实时进度订阅和连接管理。
 * @module api/progress
 */
import api, { getTempImage } from './convert.js'
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

/**
 * 订阅图片转换进度。
 *
 * @function subscribeToProgress
 * @description 通过Server-Sent Events (SSE) 实时获取图片转换进度。
 * @param {string} id - 用于标识特定转换任务的唯一ID。
 * @param {function} onMessage - 包含不同事件类型回调函数的对象。
 * @param {(data: any) => void} onMessage.onProgress - 接收进度更新的回调函数。
 * @param {(data: any) => void} onMessage.onComplete - 转换完成时的回调函数。
 * @param {(error: any) => void} onMessage.onError - 发生错误时的回调函数。
 * @param {() => void} onMessage.onOpen - SSE连接成功建立时的回调函数。
 * @param {() => void} onMessage.onClose - SSE连接关闭时的回调函数。
 */
export const subscribeToProgress = (id, onMessage) => {
  let eventSource = new EventSource(`${apiFullBasePath}/progress/${id}`)
  let shouldNotReconnect = false // 标志是否应该禁止重连
  let heartbeatCount = 0 // 心跳计数器
  
  /**
   * @event onopen
   * @description 连接打开时的处理
   */
  eventSource.onopen = () => {
    debugLog('EventSource连接已打开')
    debugLog('EventSource readyState:', eventSource.readyState)
  }

      /**
     * 处理心跳消息，用于维持连接活跃。
     */
  eventSource.addEventListener("heartbeat",(event) => {
    debugLog('收到心跳消息',event.data)
    
    // 增加心跳计数
    heartbeatCount++
    debugLog(`心跳计数: ${heartbeatCount}`)
    
    // 如果心跳次数过多，主动关闭连接并通知用户超时
    if (heartbeatCount >= 12) { // 12 * 10s = 120s
      console.warn('心跳计数达到12次，连接超时，主动关闭连接')
      shouldNotReconnect = true
      
      // 调用后端关闭接口
      try {
        closeProgress(id, 'HEARTBEAT_TIMEOUT').then(response => {
          debugLog('后端关闭接口调用成功:', response.data)
        }).catch(error => {
          console.warn('后端关闭接口调用失败:', error)
        })
      } catch (error) {
        console.warn('调用后端关闭接口时发生异常:', error)
      } finally {
        // 关闭连接
        if (eventSource && eventSource.readyState !== EventSource.CLOSED) {
          eventSource.close()
        }
      }
      
      // 通知UI连接超时
      onMessage({
        connectionStatus: 'closed',
        stage: '超时',
        message: '连接超时',
        closeReason: 'HEARTBEAT_TIMEOUT'
      })
    }
  })

      /**
     * 处理初始化消息，确认SSE连接已建立。
     */
  eventSource.addEventListener("init",(event) => {
    debugLog('已接收到后端消息：',event.data)
  })
  
      /**
     * 处理关闭消息，由服务器主动发起，通知客户端关闭连接。
     */
  eventSource.addEventListener("close",(event) => {
    debugLog('收到服务器关闭连接消息:', event.data)
    
    try {
      // 尝试解析关闭消息和关闭原因
      let closeMessage = '连接已关闭'
      let closeReason = 'TASK_COMPLETED'
      let stage = '完成'
      
      try {
        const closeData = JSON.parse(event.data)
        
        // 检查是否是新格式的关闭数据（包含closeReason）
        if (closeData.closeReason) {
          closeReason = closeData.closeReason
          closeMessage = closeData.message || closeData.progressInfo?.message || closeMessage
          
          // 根据关闭原因设置不同的状态
          switch (closeReason) {
            case 'TASK_COMPLETED':
              stage = '完成'
              break
            case 'HEARTBEAT_TIMEOUT':
              stage = '超时'
              closeMessage = closeMessage || '连接超时'
              break
            case 'ERROR_OCCURRED':
              stage = '错误'
              closeMessage = closeMessage || '处理过程中发生错误'
              break
            default:
              stage = '完成'
              break
          }
        } else {
          // 兼容旧格式的关闭数据
          closeMessage = closeData.message || closeMessage
          
          // 根据Python端的reason字段判断关闭原因
          if (closeData.reason) {
            switch (closeData.reason) {
              case 'completed':
                closeReason = 'TASK_COMPLETED'
                stage = '完成'
                break
              case 'error':
                closeReason = 'ERROR_OCCURRED'
                stage = '错误'
                break
              case 'timeout':
                closeReason = 'HEARTBEAT_TIMEOUT'
                stage = '超时'
                break
              default:
                closeReason = 'TASK_COMPLETED'
                stage = '完成'
                break
            }
          }
        }
      } catch (e) {
        // 如果不是JSON格式，直接使用原始数据作为消息
        closeMessage = event.data || closeMessage
      }
      
      debugLog('解析的关闭信息:', { closeMessage, closeReason, stage })
      
      // 根据关闭原因决定是否禁止重连
      if (closeReason === 'ERROR_OCCURRED' || closeReason === 'HEARTBEAT_TIMEOUT' || closeReason === 'TASK_COMPLETED') {
        shouldNotReconnect = true
        debugLog('因为错误/超时/完成任务关闭，禁止重连')
      }
      // 调用后端关闭接口，传递关闭原因
      try {
        debugLog('调用后端关闭接口，进度ID:', id, '关闭原因:', closeReason)
        closeProgress(id, closeReason).then(response => {
          debugLog('后端关闭接口调用成功:', response.data)
        }).catch(error => {
          console.warn('后端关闭接口调用失败:', error)
        })
      } catch (error) {
        console.warn('调用后端关闭接口时发生异常:', error)
      }
      
      // 通知UI连接已关闭，根据关闭原因设置不同状态
      onMessage({
        connectionStatus: 'closed',
        stage: stage,
        message: closeMessage,
        percentage: closeReason === 'TASK_COMPLETED' ? 100 : undefined,
        done: closeReason === 'TASK_COMPLETED',
        closeReason: closeReason
      })
    } catch (error) {
      console.error('处理关闭事件时出错:', error)
      // 即使处理出错，也要确保连接被关闭
      if (eventSource && eventSource.readyState !== EventSource.CLOSED) {
        eventSource.close()
      }
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
      debugLog('收到进度消息，解析后的数据:', data)
      
      // 收到进度消息时重置心跳计数
      heartbeatCount = 0
      
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
        
        // 重置请求超时时间
        api.defaults.timeout = 600000; // 重置为10分钟
        debugLog('请求超时时间已重置')
      } else {
        console.error('收到无效的进度数据:', data)
      }
    } catch (error) {
      console.error('解析消息数据失败:', error, '原始数据:', event.data)
      // 不要在解析错误时关闭连接，可能只是单条消息有问题
    }
  })

  /**
   * @event convertResult
   * @description 处理转换结果消息 - 接收转换完成的结果数据
   * @param {Event} event - 事件对象，包含转换结果数据
   */
  eventSource.addEventListener("convertResult",(event) => {
    try {
      const data = JSON.parse(event.data)
      debugLog('收到转换结果消息，解析后的数据:', data)
      
      // 收到转换结果消息时重置心跳计数
      heartbeatCount = 0
      
      // 新格式：直接使用ConvertResult对象
      if (data && data.id && data.filePath) {
        const resultData = {
          type: 'convertResult',
          filePath: data.filePath,
          contentType: data.contentType,
          status: data.status || 'success',
          timestamp: data.timestamp,
          percentage: 100,
          done: true,
          stage: '转换完成',
          message: data.message || '图片转换已完成'
        }

        debugLog('解析的转换结果数据:', resultData)
        onMessage(resultData)

        // 转换完成后关闭连接
        console.log('转换完成，已收到字符画图片链接')
      }
    } catch (error) {
      console.error('解析转换结果消息失败:', error, '原始数据:', event.data)
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
    if (shouldNotReconnect) {
      console.info('EventSource因错误/超时/完成任务关闭:', error)
    }else{
      console.error('EventSource连接错误:', error)
      debugLog('EventSource readyState:', eventSource.readyState)
    debugLog('EventSource URL:', eventSource.url)
    debugLog('错误详情:', {
        type: error.type,
        target: error.target,
        timeStamp: error.timeStamp
      })
    }
    // 检查是否是正常关闭（readyState为2表示CLOSED）
    if (eventSource.readyState === EventSource.CLOSED) {
      debugLog('EventSource连接已正常关闭')
      // 如果是正常关闭，不进行重连，避免不必要的错误
      return
    }
    
    // 检查是否因为错误或超时而禁止重连
    if (shouldNotReconnect) {
      debugLog('因为错误/超时/完成任务关闭，不进行重连')
      return
    }
    
    // 只有在连接意外中断时才尝试重连
    if (eventSource.readyState === EventSource.CONNECTING) {
      debugLog('连接建立中遇到错误，尝试重连...')
      
      if (retryCount < maxRetries) {
        retryCount++
        debugLog(`第${retryCount}次重连尝试...`)
        
        // 通知UI显示重连状态
        onMessage({
          connectionStatus: 'reconnecting',
          message: `连接中断，正在重连... (${retryCount}/${maxRetries})`
        })
        
        setTimeout(() => {
          try {
            // 关闭当前连接
            if (eventSource.readyState !== EventSource.CLOSED) {
              eventSource.close()
            }
            // 创建新的连接
            const newEventSource = subscribeToProgress(id, onMessage)
            // 更新引用
            Object.setPrototypeOf(eventSource, Object.getPrototypeOf(newEventSource))
            Object.assign(eventSource, newEventSource)
          } catch (reconnectError) {
            console.error('重连时出错:', reconnectError)
            onMessage({
              connectionStatus: 'failed',
              message: '重连失败，请刷新页面重试'
            })
          }
        }, 2000) // 增加重连间隔到2秒
      } else {
        console.error('重连次数已达上限，停止重连')
        try {
          if (eventSource.readyState !== EventSource.CLOSED) {
            eventSource.close()
          }
        } catch (closeError) {
          console.error('关闭连接时出错:', closeError)
        }
        onMessage({
          connectionStatus: 'failed',
          stage: '错误',
          message: '连接失败，请检查网络连接或刷新页面重试',
          percentage: 0,
          done: true
        })
      }
    } else {
      debugLog('连接状态异常，readyState:', eventSource.readyState)
    }
  }

  return eventSource
}

/**
 * @function closeProgress
 * @description 关闭进度连接，通知服务器关闭指定ID的进度连接
 * @param {string} id - 进度连接的唯一标识符
 * @param {string} closeReason - 关闭原因，可选参数
 * @returns {Promise<Object>} 关闭操作的响应对象
 */
export const closeProgress = (id, closeReason = null) => {
  const params = closeReason ? { closeReason } : {}
  return api.post(`/progress/close/${id}`, {}, {
    params,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json'  // 明确指定Accept头
    }
  })
}

/**
 * @function getTempImageFromPath
 * @description 从路径信息中拆分临时文件夹名称和文件名，然后调用getTempImage获取图片
 * @param {string} filePath - 格式为"临时文件夹名/结果文件名"的路径信息
 * @param {string} contentType - 图片的内容类型
 * @returns {Promise<Object>} 包含图片Blob数据的响应对象
 */
export const getTempImageFromPath = (filePath, contentType) => {
  // 拆分路径信息
  const pathParts = filePath.split('/')
  
  if (pathParts.length !== 2) {
    throw new Error(`无效的文件路径格式: ${filePath}，期望格式为"临时文件夹名/文件名"`)
  }
  
  const [tempDirName, fileName] = pathParts
  
  // 调用convert.js中的getTempImage函数
  return getTempImage(tempDirName, fileName, contentType)
}