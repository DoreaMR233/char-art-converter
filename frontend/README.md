# 字符画转换器前端

字符画转换器是一个基于Vue.js的Web应用，用于将普通图片转换为字符画。支持静态图片和动态图片(GIF)的转换，提供彩色和灰度两种模式，以及多种字符密度选项。

> **部署说明**: 如需了解如何使用Docker部署本服务，请参阅[Docker部署指南](./Docker.md)。

## 技术栈

- **Vue 3**：使用Vue 3的Composition API构建用户界面
- **Vite**：作为构建工具和开发服务器
- **Element Plus**：UI组件库，提供按钮、表单、进度条等组件
- **Axios**：处理HTTP请求
- **Server-Sent Events (SSE)**：用于实时进度监控

## 项目结构

``` text
├── public/                # 静态资源目录
│   └── favicon.svg       # 网站图标
├── src/                   # 源代码目录
│   ├── api/              # API调用模块
│   │   └── index.js      # API请求函数
│   ├── assets/           # 资源文件
│   │   └── main.css      # 全局样式
│   ├── App.vue           # 主应用组件
│   └── main.js           # 应用入口文件
├── index.html            # HTML模板
├── package.json          # 项目依赖和脚本
├── vite.config.js        # Vite配置文件
└── README.md             # 项目说明文档
```

## 功能特点

1. **图片转字符画**：将上传的图片转换为字符画
2. **多种转换选项**：
   - 彩色/灰度模式选择
   - 字符密度调整（低/中/高）
   - 尺寸限制选项
3. **实时进度监控**：使用SSE技术实时显示转换进度
4. **导出功能**：
   - 导出为文本文件(.txt)
   - 导出为图片文件(保留原格式)
5. **支持多种图片格式**：JPG、PNG、JPEG、GIF、WEBP、BMP
6. **文件大小限制**：默认限制上传文件大小为10MB，可通过环境变量配置
7. **响应式设计**：适配不同屏幕尺寸

## 安装指南

### 前提条件

- Node.js (v14.0.0或更高版本)
- npm (v6.0.0或更高版本)

### 安装步骤

1. 克隆项目或下载源代码

2. 安装依赖
   ```bash
   cd frontend
   npm install
   ```

3. 配置环境变量（可选）
   创建以下环境变量文件之一：
   - `.env`：所有环境都会加载的默认环境变量
   - `.env.development`：开发环境特定的环境变量
   - `.env.production`：生产环境特定的环境变量

   示例 `.env` 文件内容：
   ```
   VITE_APP_TITLE=字符画转换器
   VITE_APP_VERSION=0.0.1
   VITE_API_BASE_PATH=/api
   VITE_API_URL=http://localhost:8080
   VITE_BASE_PATH=
   ```

4. 开发模式运行
   ```bash
   npm run dev
   ```
   应用将在 http://localhost:5173 运行（可通过 VITE_PORT 环境变量修改）

5. 构建生产版本
   ```bash
   # 使用默认配置构建
   npm run build
   
   # 使用自定义资源路径前缀构建
   # 方法1：通过命令行参数
   npm run build -- --base=/char-art/
   
   # 方法2：通过环境变量文件(.env.production)
   # VITE_BASE_PATH=char-art
   npm run build
   ```
   构建后的文件将位于 `dist` 目录中

6. 预览生产构建
   ```bash
   npm run preview
   ```

## 使用说明

1. **导入图片**：点击"导入图片"按钮选择要转换的图片（支持JPG、PNG、JPEG、GIF、WEBP、BMP格式）

2. **设置转换参数**：
   - **色彩**：选择"彩色"或"灰度"模式
   - **字符密度**：选择"低"、"中"或"高"密度
   - **限制尺寸**：选择是否限制输出尺寸

3. **开始转换**：点击"绘制按钮"开始转换过程

4. **查看进度**：转换过程中可以查看进度条和详细的处理信息

5. **导出结果**：
   - 点击"导出为文本"将字符画保存为文本文件
   - 点击"导出为图片"将字符画保存为图片文件

## 配置说明

### 环境变量配置

本项目使用Vite的环境变量系统，支持以下环境变量：

#### 通用环境变量

| 变量名                  | 说明      | 默认值    |
|----------------------|---------|--------|
| `VITE_APP_TITLE`     | 应用名称    | 字符画转换器 |
| `VITE_APP_VERSION`   | 应用版本    | 0.0.1  |
| `VITE_API_BASE_PATH` | API基础路径 | /api   |

#### 开发环境变量

| 变量名              | 说明       | 默认值                     |
|------------------|----------|-------------------------|
| `VITE_API_URL`   | API服务器地址 | <http://localhost:8080> |
| `VITE_BASE_PATH` | 资源路径前缀   | (空)                     |
| `VITE_PORT`      | 开发服务器端口  | 5173                    |
| `VITE_DEBUG`     | 是否启用调试模式 | true                    |

#### 生产环境变量

| 变量名              | 说明       | 默认值                     |
|------------------|----------|-------------------------|
| `VITE_API_URL`   | API服务器地址 | <http://localhost:8080> |
| `VITE_BASE_PATH` | 资源路径前缀   | char-art                |
| `VITE_DEBUG`     | 是否启用调试模式 | false                   |
| `VITE_SOURCEMAP` | 是否启用源码映射 | false                   |

### 开发服务器配置

在 `vite.config.js` 中配置了开发服务器，默认端口为5173（可通过 VITE_PORT 环境变量修改），并设置了API代理：

```text
server: {
  port: 5173,
  proxy: {
    [apiBasePath]: {
      target: 'http://localhost:8080', // 后端API服务器地址，可通过 VITE_API_URL 环境变量修改
      changeOrigin: true,
      ws: true,
      rewrite: (path) => path
    }
  }
}
```

如需更改后端API地址，请修改环境变量 `VITE_API_URL`。
如需更改API基础路径，请修改环境变量 `VITE_API_BASE_PATH`。

## 与后端集成

前端通过API与后端服务通信，主要API端点包括：

- `/api/convert`：上传图片并转换为字符画
- `/api/text`：获取字符画的文本内容
- `/api/progress/{id}`：SSE连接，用于获取实时进度更新
- `/api/health`：检查后端服务健康状态

确保后端服务正确实现了这些API端点。

## 注意事项

1. **大文件处理**：
   - 当字符画图片大小超过300MB时，由于浏览器限制，图片将不会在界面上显示，但仍可以正常下载

2. **WEBP动图支持**：
   - 在生成WEBP格式动图的字符画时，导出的图片可能无法正常播放，建议使用其他格式的动图，或将WEBP格式动图转化成GIF格式后再进行生成

3. **网络连接**：
   - 应用使用SSE技术监控进度，需要保持与服务器的连接。如果连接中断，应用会尝试自动重连

4. **浏览器兼容性**：
   - 应用使用现代Web技术构建，建议使用最新版本的Chrome、Firefox、Safari或Edge浏览器

5. **响应式设计**：
   - 应用支持在不同屏幕尺寸上使用，但在小屏幕设备上可能需要滚动查看全部内容

## 故障排除

1. **无法连接到后端服务**：
   - 确保后端服务正在运行
   - 检查 `vite.config.js` 中的代理配置是否正确
   - 检查网络连接和防火墙设置

2. **上传失败**：
   - 确保图片格式受支持
   - 检查图片文件大小是否超过后端限制

3. **进度更新中断**：
   - 应用会尝试自动重连，如果多次失败，请刷新页面

4. **导出失败**：
   - 确保转换过程已完成
   - 检查浏览器是否允许下载文件

## 开发指南

### 添加新功能

1. 在 `src/api/index.js` 中添加新的API调用函数
2. 在 `App.vue` 中实现相应的UI和逻辑
3. 更新样式和文档

### 修改UI

1. 组件样式在 `App.vue` 的 `<style>` 部分
2. 全局样式在 `src/assets/main.css`

### 构建和部署

1. 运行 `npm run build` 生成生产版本
2. 将 `dist` 目录中的文件部署到Web服务器

## 许可证

[MIT](../LICENSE)
