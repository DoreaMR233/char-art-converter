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
          <el-form-item label="色彩：">
            <el-radio-group v-model="colorMode" fill="#6cf" :disabled="isProcessing">
              <el-radio-button label="彩色" value="color" border></el-radio-button>
              <el-radio-button label="灰度" value="grayscale" border></el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <el-form-item label="字符密度：">
            <el-select v-model="charDensity" placeholder="字符密度" style="width: 100px" :disabled="isProcessing">
              <el-option label="低" value="low"></el-option>
              <el-option label="中" value="medium"></el-option>
              <el-option label="高" value="high"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="限制尺寸：">
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
      />
      <div class="progress-info">
        <p class="progress-stage">当前阶段: {{ progressStage }}</p>
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
 * 提供图片上传、转换、导出和进度监控功能
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElLoading, ElUpload } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { convertImage, getCharText, subscribeToProgress, checkHealth } from './api'

/**
 * 状态变量 - 存储应用的各种状态
 */
// 文件和图像相关
const imageFile = ref(null)  // 上传的图片文件
const imageUrl = ref('')    // 原始图片URL
const imageUrlList = ref([])    // 原始图片URL(预览用)
const charImageUrl = ref('') // 字符画图片URL
const charImageUrlList = ref([])    // 字符画图片URL(预览用)
const charText = ref('')    // 字符画文本

// 转换参数
const colorMode = ref('color') // 颜色模式：彩色或灰度
const charDensity = ref('medium') // 字符密度：低、中、高
const limitSize = ref(true)  // 是否限制输出尺寸

// 处理状态
const isProcessing = ref(false) // 是否正在处理
const processPercentage = ref(0) // 处理进度百分比
const usageDialogVisible = ref(false) // 使用说明对话框是否可见

/**
 * DOM引用 - 用于直接操作DOM元素
 */
const originalImageContainer = ref(null) // 原始图片容器
const charImageContainer = ref(null) // 字符画图片容器

/**
 * 进度信息 - 跟踪处理进度的详细信息
 */
const progressStage = ref('') // 当前处理阶段
const currentPixel = ref(0)  // 当前处理的像素
const totalPixels = ref(0)   // 总像素数

/**
 * 警告标志 - 用于显示特定状态的警告
 */
const isLargeImage = ref(false) // 是否为大图像（可能导致性能问题）
const hasCharText = ref(false)  // 是否有可用的字符文本，控制导出为文本按钮的可用性

/**
 * 事件源变量 - 用于SSE连接
 * @type {EventSource|null}
 */
let eventSource = null



/**
 * 计算属性 - 获取上传文件大小限制（MB）
 */
const maxUploadSize = computed(() => {
  // 从环境变量获取上传大小限制，默认为10MB
  return parseInt(import.meta.env.VITE_MAX_UPLOAD_SIZE || 10)
})

/**
 * 上传前检查文件
 * 验证文件类型和大小是否符合要求
 * @param {File} file - 要上传的文件
 * @returns {boolean|Promise<Error>} - 返回布尔值表示是否通过检查，或返回Promise.reject阻止上传
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
 * 处理文件变化
 * 当用户选择文件后处理上传的图片
 * @param {Object} file - Element Plus Upload组件的文件对象
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
 * 处理图片转换
 * 将上传的图片转换为字符画，并监控处理进度
 * @async
 * @returns {Promise<void>}
 */
const processImage = async () => {
  await processImageOriginal();
}



/**
 * 原始的整体传输处理方法
 * 处理图片转换的主要逻辑，包括上传、处理和获取结果
 * @async
 * @returns {Promise<void>}
 */
const processImageOriginal = async () => {
  isProcessing.value = true
  processPercentage.value = 0
  progressStage.value = '准备处理'
  
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
        processPercentage.value = data.percentage
        console.log('更新进度百分比:', data.percentage)
      }
      
      // 更新进度阶段
      if (data.stage) {
        progressStage.value = data.stage
        console.log('更新进度阶段:', data.stage)
      } else if (data.message) {
        progressStage.value = data.message
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
            progressStage.value = `正在重新连接... ${data.message || ''}`
            break
          case 'failed':
            // 显示连接失败的提示
            progressStage.value = '连接失败，请刷新页面重试'
            break
          case 'error':
            // 显示连接错误的提示
            progressStage.value = 'SSE连接错误，请检查网络连接'
            break
        }
      }
    })
    
    // 发送请求
    console.log('发送转换请求...')
    const response = await convertImage(formData, (uploadPercentage) => {
      console.log('上传进度:', uploadPercentage)
      // 上传阶段占总进度的30%
      processPercentage.value = Math.floor(uploadPercentage * 0.3)
      progressStage.value = '上传图片'
    })
    
    console.log('请求完成，处理响应...')
    // 获取响应头中的content-type
    const contentType = response.headers['content-type']
    console.log('响应头content-type:', contentType)
    
    // 创建一个Blob对象
    const blob = new Blob([response.data], { type: contentType })
    
    // 记录响应数据大小和类型
    const responseSize = response.data.size || 0
    console.log('响应数据大小:', responseSize, '字节')
    console.log('响应数据类型:', response.data.type)
    
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
    progressStage.value = '处理完成'
    console.log('转换完成')
    ElMessage.success('转换完成')
    
  } catch (error) {
    console.error('处理失败:', error)
    // 优先使用后端返回的错误信息
    const errorMessage = error.response && error.response.data && error.response.data.message
      ? error.response.data.message
      : error.message || '未知错误'
    ElMessage.error('处理失败: ' + errorMessage)
    progressStage.value = '处理失败'
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
 * 导出为文本
 * 将字符画文本导出为.txt文件
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
 * 导出为图片
 * 将字符画图片导出为PNG或GIF文件
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
 * 显示使用说明
 * 打开包含应用使用指南的对话框
 */
const showUsageInfo = () => {
  usageDialogVisible.value = true
}

/**
 * 组件挂载时的操作
 * 在组件挂载到DOM后执行初始化逻辑
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
}

.char-art-form .el-form-item {
  margin-bottom: 0;
}

.char-art-form .el-form-item__label {
  font-weight: 500;
  color: #333;
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
  color: #606266;
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
  color: #409EFF;
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
}
</style>