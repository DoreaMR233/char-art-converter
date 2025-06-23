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
              <el-radio-button label="彩色" value="color" border></el-radio-button>
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
              @click="processImage" 
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
        <p>2. 选择色彩模式（彩色或灰度）</p>
        <p>3. 选择字符密度（低/中/高）</p>
        <p>4. 点击"绘制按钮"开始转换</p>
        <p>5. 转换完成后，可以点击"导出为文本"或"导出为图片"保存结果</p>
        <p>6. 支持静态图片和动态图片（GIF）的转换</p>
        <p>7. <strong>注意：</strong>当字符画图片大小超过300MB时，由于浏览器限制，图片将不会在界面上显示，但您仍可以正常下载其文本和图片</p>
        <p>8. <strong>注意：</strong>在生成WEBP格式动图的字符画时，导出的图片可能无法正常播放，建议使用其他格式的动图，或将WEBP格式动图转化成GIF格式后再进行生成</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 字符画转换器主组件
 * 提供图片上传、字符画转换、结果导出和实时进度监控功能
 * 支持多种图片格式和转换参数配置
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElLoading, ElUpload } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { convertImage, getCharText, subscribeToProgress, checkHealth, getTempImage } from './api'

/**
 * 响应式状态变量
 */
// 文件和图像相关状态
const imageFile = ref(null)  // 用户上传的原始图片文件
const imageUrl = ref('')    // 原始图片的预览URL
const imageUrlList = ref([])    // 原始图片URL列表，用于Element Plus图片预览
const charImageUrl = ref('') // 生成的字符画图片URL
const charImageUrlList = ref([])    // 字符画图片URL列表，用于Element Plus图片预览
const charText = ref('')    // 生成的字符画文本内容

// 转换参数配置
const colorMode = ref('color') // 颜色模式：'color'(彩色) 或 'grayscale'(灰度)
const charDensity = ref('medium') // 字符密度：'low'(低)、'medium'(中)、'high'(高)
const limitSize = ref(true)  // 是否限制输出图片尺寸以提高性能

// 处理状态控制
const isProcessing = ref(false) // 是否正在进行图片转换处理
const processPercentage = ref(0) // 当前处理进度百分比(0-100)
const usageDialogVisible = ref(false) // 使用说明对话框的显示状态

/**
 * DOM元素引用
 */
const originalImageContainer = ref(null) // 原始图片显示容器的DOM引用
const charImageContainer = ref(null) // 字符画图片显示容器的DOM引用

/**
 * 进度监控相关状态
 */
const progressStage = {
  stage: ref(''),
  message: ref('')
} // 当前处理阶段的详细信息
const currentPixel = ref(0)  // 已处理的像素数量
const totalPixels = ref(0)   // 图片总像素数量

/**
 * 状态标志
 */
const isLargeImage = ref(false) // 标识生成的字符画是否为大文件(>300MB)，影响显示方式
const hasCharText = ref(false)  // 标识是否成功获取到字符画文本，控制文本导出按钮状态

/**
 * 服务器发送事件(SSE)连接实例
 * @type {EventSource|null}
 */
let eventSource = null



/**
 * 计算属性：获取文件上传大小限制
 * @returns {number} 文件上传大小限制，单位为MB
 */
const maxUploadSize = computed(() => {
  // 从环境变量获取上传大小限制，默认为10MB
  return parseInt(import.meta.env.VITE_MAX_UPLOAD_SIZE || 10)
})

/**
 * 文件上传前的验证检查
 * 验证文件类型和大小是否符合系统要求
 * @param {File} file - 待验证的文件对象
 * @returns {boolean} 验证通过返回true，验证失败返回false
 */
const beforeUpload = (file) => {
  console.log(file)
  // 检查文件类型
  const allowedTypes = ['image/jpeg', 'image/png', 'image/jpg', 'image/gif', 'image/webp', 'image/bmp']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.error('只能上传JPG、PNG、JPEG、GIF、WEBP、BMP格式的图片!')
    return false
  }
  console.log(file.size)
  console.log(maxUploadSize.value)
  // 检查文件大小
  const isLessThanLimit = file.size / 1024 / 1024 < maxUploadSize.value
  if (!isLessThanLimit) {
    ElMessage.error(`上传图片大小不能超过 ${maxUploadSize.value}MB!`)
    return false
  }
  
  return true
}

/**
 * 处理文件选择变化事件
 * 当用户通过上传组件选择新文件时触发，更新相关状态
 * @param {Object} file - Element Plus Upload组件的文件对象
 * @param {File} file.raw - 原始文件对象
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
      
      console.log('已更新图片:', imageFile.value.name)
    }
  }
}

/**
 * 启动图片转换处理
 * 调用核心转换方法将图片转换为字符画
 * @async
 * @returns {Promise<void>}
 */
const processImage = async () => {
  await processImageOriginal();
}



/**
 * 图片转换核心处理方法
 * 执行完整的图片转字符画流程：文件上传、进度监控、结果获取
 * @async
 * @returns {Promise<void>}
 */
const processImageOriginal = async () => {
  isProcessing.value = true
  processPercentage.value = 0
  progressStage.stage.value = '准备处理'
  
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
    const requestId = Date.now().toString()
    formData.append('progressId', requestId)
    console.log('请求ID:', requestId)
    
    // 创建事件源用于接收进度更新
    console.log('订阅进度更新，请求ID:', requestId)
    eventSource = subscribeToProgress(requestId, (data) => {
      console.log('进度更新:', data)
      
      // 确保percentage是数字并更新进度条
      if (data.percentage !== undefined) {
        processPercentage.value = parseFloat(data.percentage.toFixed(2));
        console.log('更新进度百分比:', data.percentage)
      }
      
      // 更新进度阶段
      if (data.stage) {
        progressStage.stage.value = data.stage
        console.log('更新进度阶段:', data.stage)
      }
      if (data.message) {
        progressStage.message.value = data.message
        console.log('更新进度消息:', data.message)
      }
      
      // 更新像素处理信息
      if (data.currentPixel !== undefined && data.totalPixels !== undefined) {
        currentPixel.value = data.currentPixel
        totalPixels.value = data.totalPixels
        console.log('更新像素信息:', data.currentPixel, '/', data.totalPixels)
      }
      
      // 处理连接状态更新
      if (data.connectionStatus) {
        console.log('连接状态更新:', data.connectionStatus)
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
            break
          case 'error':
            // 显示连接错误的提示
            progressStage.stage.value = 'SSE连接错误'
            progressStage.message.value = 'SSE连接错误，请检查网络连接'
            break
          case 'closed':
            // 显示连接已关闭的提示
            progressStage.stage.value = 'SSE超时'
            progressStage.message.value = '由于服务器长时间为响应，已关闭SSE消息通知连接'
        }
      }
    })
    
    // 发送请求
    console.log('发送转换请求...')
    const response = await convertImage(formData, (uploadPercentage) => {
      console.log('上传进度:', uploadPercentage)
      // 上传阶段占总进度的30%
      processPercentage.value = Math.floor(uploadPercentage * 0.3)
      progressStage.stage.value = '上传图片'
      progressStage.message.value = '上传图片'
    })
    
    console.log('请求完成，处理响应...')
    console.log('响应数据:', response.data)
    
    // 从响应中获取文件路径和内容类型
    const { filePath, contentType } = response.data
    console.log('文件路径:', filePath)
    console.log('内容类型:', contentType)
    
    // 使用文件路径获取图片数据
    console.log('获取图片数据...')
    const imageResponse = await getTempImage(filePath, contentType)
    
    // 创建一个Blob对象
    const blob = new Blob([imageResponse.data], { type: contentType })
    
    // 记录响应数据大小和类型
    const responseSize = imageResponse.data.size || 0
    console.log('响应数据大小:', responseSize, '字节')
    console.log('响应数据类型:', imageResponse.data.type)
    
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
      console.log('大文件Blob URL已创建但不显示:', charImageUrl.value)
    } else {
      // 正常大小的图片，重置大图片标志并显示
      isLargeImage.value = false
      console.log('Blob URL:', charImageUrl.value)
      
      // 加载图片
      const img = new Image()
      img.onload = () => console.log('图片加载成功')
      img.onerror = (e) => console.error('图片加载失败:', e)
      img.src = charImageUrl.value
    }
    
    // 获取字符画文本
    console.log('获取字符画文本...')
    try {
      const textResponse = await getCharText(imageFile.value.name)
      
      // axios已经自动解析了JSON响应，直接使用textResponse.data
      const jsonData = textResponse.data
      console.log('字符画文本响应:', jsonData)
      
      // 设置字符画文本和可用性状态
      if (jsonData.find) {
        charText.value = jsonData.text
        hasCharText.value = true
        console.log('找到字符画文本')
      } else {
        charText.value = ''
        hasCharText.value = false
        console.log('未找到字符画文本')
        ElMessage.warning('未找到字符画文本，无法导出为文本')
      }
    } catch (e) {
      console.error('获取字符画文本失败:', e)
      charText.value = ''
      hasCharText.value = false
      ElMessage.error('获取字符画文本失败')
    }
    
    processPercentage.value = 100
    progressStage.stage.value = '处理完成'
    progressStage.message.value = '处理完成'
    console.log('转换完成')
    ElMessage.success('转换完成')
    
  } catch (error) {
    console.error('处理失败:', error)
    // 优先使用后端返回的错误信息
    const errorMessage = error.response && error.response.data && error.response.data.message
      ? error.response.data.message
      : error.message || '未知错误'
    ElMessage.error('处理失败: ' + errorMessage)
    progressStage.stage.value = '处理失败'
    progressStage.message.value = '处理失败'
  } finally {
    // 确保关闭EventSource连接
    if (eventSource) {
      console.log('关闭EventSource连接')
      eventSource.close()
      eventSource = null
    }
    isProcessing.value = false
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
      console.log('后端服务健康状态:', response.data)
      if (response.data.status === 'UP' && response.data.webpProcessor === 'UP') {
        ElMessage.success('后端服务正常运行')
        console.log('后端服务正常运行')
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
  margin: 20px 0;
  padding: 0 20px;
  max-width: 800px;
  margin-left: auto;
  margin-right: auto;
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