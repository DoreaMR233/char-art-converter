<template>
  <div class="container">
    <h1 class="title">字符画转换器</h1>
    
    <div class="button-container">
      <el-upload
        class="upload-component"
        action=""
        :auto-upload="false"
        :show-file-list="false"
        :on-change="handleFileChange"
        :before-upload="beforeUpload"
        accept="image/jpeg,image/png,image/jpg,image/gif,image/webp,image/bmp"
      >
        <el-button 
          type="primary" 
          class="action-button"
          :disabled="isProcessing"
        >导入图片</el-button>
      </el-upload>
      
      <el-button 
        type="success" 
        class="action-button"
        @click="exportAsText" 
        :disabled="!hasCharText || isProcessing"
      >导出为文本</el-button>
      
      <el-button 
        type="success" 
        class="action-button"
        @click="exportAsImage" 
        :disabled="!charImageUrl || isProcessing"
      >导出为图片</el-button>
    </div>
    
    <el-row class="options-row" justify="center">
      <el-col :xs="24" :sm="24" :md="20" :lg="18">
        <el-form :inline="true" class="char-art-form" justify="center">
          <el-form-item label="色彩：" class="form-label">
            <el-radio-group v-model="colorMode" fill="#6cf" :disabled="isProcessing">
              <el-radio-button label="彩色字符" value="color" border></el-radio-button>
              <el-radio-button label="彩色背景" value="colorBackground" border></el-radio-button>
              <el-radio-button label="灰度" value="grayscale" border></el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <el-form-item label="字符密度：" class="form-label">
            <el-select v-model="charDensity" placeholder="字符密度" style="width: 100px" :disabled="isProcessing">
              <el-option label="低" value="low"></el-option>
              <el-option label="中" value="medium"></el-option>
              <el-option label="高" value="high"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="限制尺寸：" class="form-label">
            <el-select v-model="limitSize" placeholder="是否限制尺寸" style="width: 100px" :disabled="isProcessing">
              <el-option label="是" :value="true"></el-option>
              <el-option label="否" :value="false"></el-option>
            </el-select>
          </el-form-item>
          
          <el-form-item>  
            <el-button 
              type="primary" 
              @click="processImageOriginal" 
              :disabled="!imageFile || isProcessing"
              style="width: 100%"
            >绘制按钮</el-button>
          </el-form-item>
          <el-form-item>
            <el-button 
              type="info" 
              @click="showUsageInfo" 
              :icon="QuestionFilled" 
              style="width: 100%"
            >使用说明</el-button>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>

    
    <!-- 进度条和进度信息 -->
    <div v-if="isProcessing" class="progress-container">
      <el-progress 
        :percentage="processPercentage" 
        :stroke-width="20" 
        :show-text="true"
        :status="processPercentage >= 100 ? 'success' : ''"
        class="light-progress"
      />
      <div class="progress-info">
        <p class="progress-stage" v-if="progressStage.stage">当前阶段: {{ progressStage.stage.value }}</p>
        <p class="progress-stage" v-if="progressStage.message">当前信息: {{ progressStage.message.value }}</p>
        <p v-if="totalPixels > 0" class="progress-pixels">
          处理像素: {{ currentPixel.toLocaleString() }} / {{ totalPixels.toLocaleString() }}
          <span class="pixel-percentage" v-if="totalPixels > 0">
            ({{ Math.floor((currentPixel / totalPixels) * 100) }}%)
          </span>
        </p>
      </div>
    </div>
    
    <div class="image-container">
      <div class="image-box">
        <div class="image-box-title">原图</div>
        <div class="image-content" ref="originalImageContainer">
          <el-image v-if="imageUrl" :src="imageUrl" alt="原图" fit="fill" :preview-src-list="imageUrlList" />
        </div>
      </div>
      
      <div class="image-box">
        <div class="image-box-title">字符画</div>
        <div class="image-content" ref="charImageContainer">
          <el-image v-if="charImageUrl && !isLargeImage" :src="charImageUrl" alt="字符画" fit="fill" :preview-src-list="charImageUrlList"/>
          <div v-else-if="charImageUrl && isLargeImage" class="large-image-warning">
            <el-alert
              title="图片过大"
              type="warning"
              description="字符画图片过于庞大（超过300MB），无法在浏览器中展示，但您仍可以正常下载其文本和图片。"
              :closable="false"
              show-icon
            />
          </div>
        </div>
      </div>
    </div>
    
    <el-dialog v-model="usageDialogVisible" title="使用说明" width="50%">
      <div>
        <p>1. 点击"导入图片"按钮选择要转换的图片（仅支持JPG、PNG、JPEG、WEBP、GIF、BMP格式）</p>
        <!-- <p>1. 点击"导入图片"按钮选择要转换的图片</p> -->
        <p>2. 选择色彩模式：</p>
        <ul>
          <li><strong>彩色：</strong>生成彩色字符画，保留原图色彩信息</li>
          <li><strong>彩色背景：</strong>生成带有彩色背景的字符画，字符颜色与背景相似但有足够对比度</li>
          <li><strong>灰度：</strong>生成黑白字符画，仅保留明暗信息</li>
        </ul>
        <p>3. 选择字符密度：</p>
        <ul>
          <li><strong>低密度：</strong>使用较少的字符，生成速度快，细节较少</li>
          <li><strong>中密度：</strong>平衡的字符数量，适合大多数图片</li>
          <li><strong>高密度：</strong>使用更多字符，生成速度较慢，但能呈现更多细节</li>
        </ul>
        <p>4. 点击"绘制按钮"开始转换</p>
        <p>5. 转换完成后，可以点击"导出为文本"或"导出为图片"保存结果</p>
        <p>6. 支持静态图片和动态图片（GIF、WEBP）的转换</p>
        <p>7. <strong>注意：</strong>当字符画图片大小超过300MB时，由于浏览器限制，图片将不会在界面上显示，但您仍可以正常下载其文本和图片</p>
        <p>8. <strong>注意：</strong>在生成WEBP格式动图的字符画时，导出的图片可能无法正常播放，建议使用其他格式的动图，或将WEBP格式动图转化成GIF格式后再进行生成</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * @file App.vue
 * @description 字符画转换器主组件，提供图片上传、参数配置、字符画转换、结果预览与导出，以及实时进度监控功能。
 * @vue-component
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElLoading, ElUpload } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { convertImage, getCharText, checkHealth } from './api/convert'
import { subscribeToProgress, getTempImageFromPath } from './api/progress'
import { debugLog } from './utils/debug.js'

/**
 * @section 响应式状态变量
 * @description 定义了组件中所有需要响应式跟踪的状态。
 */
/** @type {Ref<File | null>} 用户上传的原始图片文件 */
const imageFile = ref(null)
/** @type {Ref<string>} 原始图片的预览URL */
const imageUrl = ref('')
/** @type {Ref<string[]>} 原始图片URL列表，用于Element Plus图片预览 */
const imageUrlList = ref([])
/** @type {Ref<string>} 生成的字符画图片URL */
const charImageUrl = ref('')
/** @type {Ref<string[]>} 字符画图片URL列表，用于Element Plus图片预览 */
const charImageUrlList = ref([])
/** @type {Ref<string>} 生成的字符画文本内容 */
const charText = ref('')

/** @type {Ref<'color' | 'colorBackground' | 'grayscale'>} 颜色模式 */
const colorMode = ref('color')
/** @type {Ref<'low' | 'medium' | 'high'>} 字符密度 */
const charDensity = ref('medium')
/** @type {Ref<boolean>} 是否限制输出图片尺寸以提高性能 */
const limitSize = ref(true)

/** @type {Ref<boolean>} 是否正在进行图片转换处理 */
const isProcessing = ref(false)
/** @type {Ref<number>} 当前处理进度百分比(0-100) */
const processPercentage = ref(0)
/** @type {Ref<boolean>} 使用说明对话框的显示状态 */
const usageDialogVisible = ref(false)

/**
 * @section DOM元素引用
 * @description 用于直接操作DOM的引用。
 */
/** @type {Ref<HTMLElement | null>} 原始图片显示容器的DOM引用 */
const originalImageContainer = ref(null)
/** @type {Ref<HTMLElement | null>} 字符画图片显示容器的DOM引用 */
const charImageContainer = ref(null)

/**
 * @section 进度监控相关状态
 * @description 跟踪字符画转换过程中的详细进度。
 */
/** @type {{stage: Ref<string>, message: Ref<string>}} 当前处理阶段的详细信息 */
const progressStage = {
  stage: ref(''),
  message: ref(''),
}
/** @type {Ref<number>} 已处理的像素数量 */
const currentPixel = ref(0)
/** @type {Ref<number>} 图片总像素数量 */
const totalPixels = ref(0)

/**
 * @section 状态标志
 * @description 用于控制UI行为的布尔标志。
 */
/** @type {Ref<boolean>} 标识生成的字符画是否为大文件(>300MB)，影响显示方式 */
const isLargeImage = ref(false)
/** @type {Ref<boolean>} 标识是否成功获取到字符画文本，控制文本导出按钮状态 */
const hasCharText = ref(false)

/**
 * @section SSE连接管理
 * @description 管理与后端的Server-Sent Events连接，用于实时进度更新。
 */
/** @type {EventSource | null} SSE连接实例 */
let eventSource = null
/** @type {boolean} 控制是否应该关闭SSE连接的标志 */
let shouldCloseConnection = false
/** @type {number | null} 进度条延时关闭定时器ID */
let progressCloseTimer = null

/**
 * 延时关闭进度条。
 * @description 在转换完成或出错后，延时3秒关闭进度条显示，以提供更好的用户反馈。
 */
const closeProgressWithDelay = () => {
  // 清除之前的定时器
  if (progressCloseTimer) {
    clearTimeout(progressCloseTimer)
  }
  
  // 设置3秒后关闭进度条
  progressCloseTimer = setTimeout(() => {
    isProcessing.value = false
    progressCloseTimer = null
    debugLog('进度条已延时关闭')
  }, 3000)
  
  debugLog('进度条将在3秒后关闭')
}

/**
 * 重置处理状态。
 * @description 在开始新的转换任务前，重置与上一次任务相关的状态，如SSE连接关闭标志和定时器。
 */
const resetProcessingState = () => {
  // 重置连接关闭标志
  shouldCloseConnection = false
  
  // 清除之前的进度条关闭定时器
  if (progressCloseTimer) {
    clearTimeout(progressCloseTimer)
    progressCloseTimer = null
  }
}



/**
 * 计算属性，获取文件上传大小限制。
 * @returns {number} 文件上传大小限制，单位为MB。
 */
const maxUploadSize = computed(() => {
  // 从环境变量获取上传大小限制，默认为10MB
  return parseInt(import.meta.env.VITE_MAX_UPLOAD_SIZE || 10)
})

/**
 * 文件上传前的验证检查。
 * @description 验证文件的类型和大小是否符合系统要求。
 * @param {File} file - 待验证的文件对象。
 * @returns {boolean} 如果文件有效则返回 `true`，否则返回 `false`。
 */
const beforeUpload = (file) => {
  debugLog(file)
  // 检查文件类型
  const allowedTypes = ['image/jpeg', 'image/png', 'image/jpg', 'image/gif', 'image/webp', 'image/bmp']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('只能上传JPG、PNG、JPEG、GIF、WEBP、BMP格式的图片!')
    return false
  }
  debugLog(file.size)
  debugLog(maxUploadSize.value)
  // 检查文件大小
  const isLessThanLimit = file.size / 1024 / 1024 < maxUploadSize.value
  if (!isLessThanLimit) {
    ElMessage.error(`上传图片大小不能超过 ${maxUploadSize.value}MB!`)
    return false
  }
  
  return true
}

/**
 * 处理文件选择变化事件。
 * @description 当用户通过上传组件选择新文件时触发，更新相关状态并重置旧的预览信息。
 * @param {object} file - Element Plus Upload组件的文件对象。
 * @param {File} file.raw - 原始文件对象。
 */
const handleFileChange = (file) => {
  if (!file || !file.raw) return
  hasCharText.value = false
  isLargeImage.value = false
  if(beforeUpload(file.raw)){
    // 设置当前文件，覆盖之前的文件
    imageFile.value = file.raw
    if (imageFile.value) {
      // 如果之前有创建的对象URL，先释放它以避免内存泄漏
      if (imageUrl.value && imageUrl.value.startsWith('blob:')) {
        URL.revokeObjectURL(imageUrl.value)
      }
      
      // 为新图片创建对象URL
      imageUrl.value = URL.createObjectURL(imageFile.value)
      imageUrlList.value = [imageUrl.value]
      
      // 重置字符画相关状态
      if (charImageUrl.value && charImageUrl.value.startsWith('blob:')) {
        URL.revokeObjectURL(charImageUrl.value)
      }
      charImageUrl.value = ''
      charImageUrlList.value = []
      charText.value = ''
      isLargeImage.value = false
      
      debugLog('已更新图片:', imageFile.value.name)
    }
  }
}


/**
 * 图片转换核心处理方法。
 * @description 执行完整的图片转字符画流程：构建表单数据、订阅SSE进度、发送转换请求，并处理各种回调。
 * @async
 * @returns {Promise<void>}
 */
const processImageOriginal = async () => {
  isProcessing.value = true
  processPercentage.value = 0
  progressStage.stage.value = '准备处理'
  
  // 重置处理状态
  resetProcessingState()
  
  try {
    if (!imageFile.value) {
      ElMessage.error('请先选择图片')
      return
    }
    
    const formData = new FormData()
    formData.append('image', imageFile.value)
    formData.append('density', charDensity.value)
    formData.append('colorMode', colorMode.value)
    formData.append('limitSize', limitSize.value)
    
    // 添加请求ID到FormData，后端可以使用这个ID来发送进度更新
    const requestId = crypto.randomUUID()
    formData.append('progressId', requestId)
    debugLog('请求ID:', requestId)
    
    // 订阅进度更新
    debugLog('订阅进度更新，请求ID:', requestId)
    eventSource = subscribeToProgress(requestId, async (data) => {
      debugLog('进度更新:', data)
      
      // 确保percentage是数字并更新进度条
      if (data.percentage !== undefined) {
        processPercentage.value = parseFloat(data.percentage.toFixed(2));
        debugLog('更新进度百分比:', data.percentage)
      }
      
      // 更新进度阶段
      if (data.stage) {
        progressStage.stage.value = data.stage
        debugLog('更新进度阶段:', data.stage)
      }
      if (data.message) {
        progressStage.message.value = data.message
        debugLog('更新进度消息:', data.message)
      }
      
      // 更新像素处理信息
      if (data.currentPixel !== undefined && data.totalPixels !== undefined) {
        currentPixel.value = data.currentPixel
        totalPixels.value = data.totalPixels
        debugLog('更新像素信息:', data.currentPixel, '/', data.totalPixels)
      }
      
      // 处理转换结果消息
      if (data.type === 'convertResult') {
        console.log('收到转换结果，开始获取图片和文本...')
        await handleConvertResult(data.filePath, data.contentType)
        return
      }
      
      // 处理连接状态更新
        if (data.connectionStatus) {
          debugLog('连接状态更新:', data.connectionStatus)
        // 根据连接状态更新UI
        switch (data.connectionStatus) {
          case 'reconnecting':
            // 显示重连中的提示
            progressStage.stage.value = '重新连接'
            progressStage.message.value = `正在重新连接... ${data.message || ''}`
            break
          case 'failed':
            // 显示连接失败的提示
            progressStage.stage.value = '连接失败'
            progressStage.message.value = '连接失败，请刷新页面重试'
            // 连接失败时关闭进度条
            shouldCloseConnection = true
            closeProgressWithDelay()
            break
          case 'error':
            // 显示连接错误的提示
            progressStage.stage.value = 'SSE连接错误'
            progressStage.message.value = 'SSE连接错误，请检查网络连接'
            // 连接错误时关闭进度条
            shouldCloseConnection = true
            closeProgressWithDelay()
            break
          case 'closed':
            // 根据关闭原因显示不同的提示
            switch (data.closeReason) {
              case 'HEARTBEAT_TIMEOUT':
                progressStage.stage.value = 'SSE超时'
                progressStage.message.value = '由于服务器长时间未响应，已关闭SSE消息通知连接'
                break
              case 'ERROR_OCCURRED':
                progressStage.stage.value = 'SSE错误'
                progressStage.message.value = '处理过程中发生错误，已关闭SSE连接'
                break
              case 'TASK_COMPLETED':
                progressStage.stage.value = '任务完成'
                progressStage.message.value = '图片转换完成，SSE连接已正常关闭'
                break
              default:
                progressStage.stage.value = 'SSE关闭'
                progressStage.message.value = 'SSE连接已关闭'
                break
            }
            // 连接关闭时关闭进度条
            shouldCloseConnection = true
            closeProgressWithDelay()
        }
      }
    })
    
    // 发送请求
    debugLog('发送转换请求...')
    const response = await convertImage(formData, (uploadPercentage) => {
      debugLog('上传进度:', uploadPercentage)
      // 上传阶段占总进度的30%
      processPercentage.value = Math.floor(uploadPercentage * 0.3)
      progressStage.stage.value = '上传图片'
      progressStage.message.value = '上传图片'
    })
    
    debugLog('请求完成，等待SSE消息...')
    debugLog('响应数据:', response.data)
    
    // 注意：现在不再直接获取图片和文本，而是等待SSE的convertResult消息
    
  } catch (error) {
    console.error('处理失败:', error)
    // 优先使用后端返回的错误信息
    const errorMessage = error.response && error.response.data && error.response.data.message
      ? error.response.data.message
      : error.message || '未知错误'
    ElMessage.error('处理失败: ' + errorMessage)
    progressStage.stage.value = '处理失败'
    progressStage.message.value = '处理失败'
    
    // 发生错误时设置应该关闭连接的标志
    shouldCloseConnection = true
    
    // 发生错误时延时关闭进度条
    closeProgressWithDelay()
    
    // 确保关闭加载提示（如果存在）
    if (typeof loadingInstance !== 'undefined' && loadingInstance) {
      loadingInstance.close()
    }
  } finally {
    // 只有在应该关闭连接时才关闭EventSource连接
    if (eventSource && shouldCloseConnection) {
      debugLog('关闭EventSource连接')
      eventSource.close()
      eventSource = null
    }
    // 注意：不再直接设置 isProcessing.value = false
    // 现在由 closeProgressWithDelay() 函数来延时关闭进度条
  }
}

/**
 * 处理转换结果的方法
 * 当收到SSE的convertResult消息时调用，获取图片和文本
 * @param {string} filePath - 文件路径
 * @param {string} contentType - 内容类型
 */
const handleConvertResult = async (filePath, contentType) => {
  try {
    debugLog('文件路径:', filePath)
    debugLog('内容类型:', contentType)
    
    // 获取图片数据
    debugLog('获取图片数据...')
    
    // 显示加载提示
    const loadingInstance = ElLoading.service({
      lock: true,
      text: '获取图片数据中...',
      background: 'rgba(0, 0, 0, 0.7)'
    })
    
    let imageLoadSuccess = false
    let textLoadSuccess = false
    
    try {
      const imageResponse = await getTempImageFromPath(filePath, contentType)
      
      // 创建一个Blob对象
      const blob = new Blob([imageResponse.data], { type: contentType })
      
      // 记录响应数据大小和类型
      const responseSize = imageResponse.data.size || imageResponse.data.byteLength || 0
      debugLog('响应数据大小:', responseSize, '字节')
      debugLog('响应数据类型:', imageResponse.data.type)
      
      // 检查响应大小是否超过300MB
      const MAX_SIZE = 300 * 1024 * 1024 // 300MB in bytes
      
      // 创建URL以便下载
      charImageUrl.value = URL.createObjectURL(blob)
      charImageUrlList.value = [charImageUrl.value]
      
      if (responseSize > MAX_SIZE) {
        // 如果响应大小超过300MB，设置大图片标志并显示警告消息
        isLargeImage.value = true
        ElMessage.warning({
          message: '字符画图片过于庞大（超过300MB），无法在浏览器中展示，但您仍可以正常下载其文本和图片。',
          duration: 8000,
          showClose: true
        })
        debugLog('大文件Blob URL已创建但不显示:', charImageUrl.value)
        // 关闭加载提示
        loadingInstance.close()
        imageLoadSuccess = true
      } else {
        // 正常大小的图片，重置大图片标志并显示
        isLargeImage.value = false
        debugLog('Blob URL:', charImageUrl.value)
        
        // 加载图片
        const img = new Image()
        img.onload = () => {
          debugLog('图片加载成功')
          // 关闭加载提示
          loadingInstance.close()
        }
        img.onerror = (e) => {
          console.error('图片加载失败:', e)
          // 关闭加载提示
          loadingInstance.close()
        }
        img.src = charImageUrl.value
        imageLoadSuccess = true
      }
    } catch (e) {
      console.error('获取图片数据失败:', e)
      ElMessage.warning('获取图片数据失败')
      loadingInstance.close()
      throw new Error('获取图片数据失败: ' + (e.message || '未知错误'))
    }
    
    // 获取字符画文本
    debugLog('获取字符画文本...')
    try {
      const textResponse = await getCharText(imageFile.value.name)
      
      // axios已经自动解析了JSON响应，直接使用textResponse.data
      const jsonData = textResponse.data
      debugLog('字符画文本响应:', jsonData)
      
      // 设置字符画文本和可用性状态
      if (jsonData.find) {
        charText.value = jsonData.text
        hasCharText.value = true
        debugLog('找到字符画文本')
        textLoadSuccess = true
      } else {
        charText.value = ''
        hasCharText.value = false
        debugLog('未找到字符画文本')
        textLoadSuccess = false
      }
    } catch (e) {
      console.error('获取字符画文本失败:', e)
      charText.value = ''
      hasCharText.value = false
      throw new Error('获取字符画文本失败: ' + (e.message || '未知错误'))
    }
    
    // 只有在图片和文本都成功获取时才显示完全成功
    processPercentage.value = 100
    progressStage.stage.value = '处理完成'
    progressStage.message.value = '处理完成'
    debugLog('转换完成')
    
    if (imageLoadSuccess && textLoadSuccess) {
      ElMessage.success('转换完成')
    } else if (imageLoadSuccess && !textLoadSuccess) {
      // 检查是否为动图
      const fileExt = imageFile.value.name.split('.').pop().toLowerCase()
      if (fileExt === 'gif' || fileExt === 'webp') {
        ElMessage.success('动图转换完成（动图暂不支持字符画文本导出）')
      } else {
        ElMessage.warning('图片转换完成，但字符画文本获取失败')
      }
    } else if(!imageLoadSuccess){
      ElMessage.error('获取图片失败')
    } else {
      ElMessage.error('转换失败，请刷新页面后重试')
    }
    
    // 转换完成时设置应该关闭连接的标志并关闭连接
    shouldCloseConnection = true
    if (eventSource) {
      debugLog('转换完成，关闭EventSource连接')
      eventSource.close()
      eventSource = null
    }
    
    // 转换完成时延时关闭进度条
    closeProgressWithDelay()
    
  } catch (error) {
    console.error('获取转换结果失败:', error)
    const errorMessage = error.response && error.response.data && error.response.data.message
      ? error.response.data.message
      : error.message || '获取转换结果失败'
    ElMessage.error('获取转换结果失败: ' + errorMessage)
    
    progressStage.stage.value = '获取结果失败'
    progressStage.message.value = '获取转换结果失败'
    
    // 获取转换结果失败时设置应该关闭连接的标志并关闭连接
    shouldCloseConnection = true
    if (eventSource) {
      debugLog('获取转换结果失败，关闭EventSource连接')
      eventSource.close()
      eventSource = null
    }
    
    // 获取转换结果失败时延时关闭进度条
    closeProgressWithDelay()
  }
}

/**
 * 导出字符画文本文件
 * 将生成的字符画文本内容保存为.txt格式文件
 * @returns {void}
 */
const exportAsText = () => {
  if (!hasCharText.value) {
    ElMessage.warning('没有可导出的字符画文本')
    return
  }
  
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '导出文本中...',
    background: 'rgba(0, 0, 0, 0.7)'
  })
  
  try {
    const blob = new Blob([charText.value], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const fileName = imageFile.value.name.split('.')[0] + 'toCharImg.txt'
    
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } finally {
    loadingInstance.close()
  }
}

/**
 * 导出字符画图片文件
 * 将生成的字符画图片保存为原格式文件(PNG/GIF/WEBP等)
 * @returns {void}
 */
const exportAsImage = () => {
  if (!charImageUrl.value) {
    ElMessage.warning('没有可导出的字符画图片')
    return
  }
  
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '导出图片中...',
    background: 'rgba(0, 0, 0, 0.7)'
  })
  
  try {
    const a = document.createElement('a')
    const fileExt = imageFile.value.name.split('.').pop()
    const fileName = imageFile.value.name.split('.')[0] + 'toCharImg.' + fileExt
    
    a.href = charImageUrl.value
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } finally {
    loadingInstance.close()
  }
}

/**
 * 显示使用说明对话框
 * 打开包含应用功能介绍和操作指南的弹窗
 * @returns {void}
 */
const showUsageInfo = () => {
  usageDialogVisible.value = true
}

/**
 * 组件挂载生命周期钩子
 * 组件挂载到DOM后执行初始化操作，包括后端服务健康检查
 * @returns {void}
 */
onMounted(() => {
  // 初始化滚动条
  
  // 检查后端服务健康状态
  checkHealth()
    .then(response => {
      debugLog('后端服务健康状态:', response.data)
        if (response.data.status === 'UP' && response.data.webpProcessor === 'UP') {
          ElMessage.success('后端服务正常运行')
          debugLog('后端服务正常运行')
      }else if(response.data.status === 'UP' && response.data.webpProcessor === 'OFF') {
        ElMessage.warning('后端服务正常运行，但WebP处理服务未启用，无法处理Webp格式动图')
      }else {
        ElMessage.warning('后端服务可能存在问题，请刷新页面重试')
      }
    })
    .catch(error => {
      console.error('健康检查失败:', error)
      ElMessage.error('无法连接到后端服务，请检查网络连接或联系管理员')
    })
})
</script>

<style scoped>

.button-container {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 30px; /* 统一设置按钮之间的间距 */
  margin-bottom: 30px;
  padding: 0 20px;
}

.action-button {
  width: 160px; /* 统一设置按钮宽度 */
  height: 40px; /* 统一设置按钮高度 */
  font-size: 16px;
}

.large-image-warning {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  padding: 20px;
}

.options-row {
  margin-bottom: 20px;
}

.char-art-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 15px;
  height: 100%;
  background-color: white;
  border-radius: 15px;
  padding: 20px;
  box-shadow: 0 5px 15px rgba(0,0,0,0.1);
}

.char-art-form .el-form-item {
  margin-bottom: 0;
}

.char-art-form .el-form-item__label {
  font-weight: 500;
  color: #333;
}

.form-label {
  margin-right: 5px;
}

.form-label :deep(.el-form-item__label) {
  color: #333;
  font-weight: 500;
}

.char-art-form .el-button {
  transition: all 0.3s ease;
}

.char-art-form .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}



.light-progress :deep(.el-progress__text) {
  color: #ffffff !important;
  font-weight: 600;
  text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.5);
}

.char-art-form .el-radio-button__inner {
  padding: 8px 15px;
}

.progress-container {
  padding: 0 20px;
  max-width: 800px;
  margin: 20px auto;
}

.progress-info {
  margin-top: 10px;
  text-align: center;
  color: #e9ecef;
}

.progress-stage {
  font-weight: 500;
  margin-bottom: 5px;
  font-size: 1.1em;
}

.progress-pixels {
  font-size: 0.9em;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 5px;
}

.pixel-percentage {
  color: #20578f;
  font-weight: 500;
}

/* 确保按钮在小屏幕上也能正常显示 */
@media screen and (max-width: 768px) {
  .button-container {
    flex-direction: column;
    gap: 15px;
  }
  
  .action-button {
    width: 200px; /* 在小屏幕上增加按钮宽度 */
  }
  
  .options-row .el-form-item {
    margin-bottom: 10px;
  }
  
  .char-art-form {
    flex-direction: column;
    padding: 15px 10px;
  }
  
  .char-art-form .el-form-item {
    width: 100%;
    margin-right: 0;
  }
}
</style>