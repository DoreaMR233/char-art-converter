# 字符画转换器前端服务

字符画转换器前端服务是一个基于Vue 3的现代化Web应用程序，提供直观的用户界面来将图片转换为字符画。支持静态图片（JPG、PNG等）和动态图片（GIF、WebP）的处理，提供彩色和灰度两种模式，以及多种字符密度选项。

## 目录

- [字符画转换器前端服务](#字符画转换器前端服务)
  - [目录](#目录)
  - [项目结构](#项目结构)
  - [技术栈](#技术栈)
  - [使用说明](#使用说明)
    - [本地部署说明](#本地部署说明)
      - [环境要求](#环境要求)
      - [安装步骤](#安装步骤)
      - [注意事项](#注意事项)
    - [Docker部署说明](#docker部署说明)
    - [配置文件说明](#配置文件说明)
      - [环境变量文件](#环境变量文件)
      - [主要配置项](#主要配置项)
        - [通用环境变量](#通用环境变量)
        - [开发环境变量](#开发环境变量)
        - [生产环境变量](#生产环境变量)
      - [Vite配置文件](#vite配置文件)
      - [API代理配置](#api代理配置)
      - [功能特性配置](#功能特性配置)
  - [许可证](#许可证)

## 项目结构

```text
frontend/
├── public/                    # 静态资源目录
│   └── favicon.svg           # 网站图标
├── src/                       # 源代码目录
│   ├── api/                  # API调用模块
│   │   └── index.js          # API请求函数和SSE连接
│   ├── assets/               # 资源文件
│   │   └── main.css          # 全局样式
│   ├── App.vue               # 主应用组件
│   └── main.js               # 应用入口文件
├── .dockerignore             # Docker忽略文件
├── .env.example              # 环境变量示例文件
├── .env.development.example  # 开发环境变量示例
├── .env.production.example   # 生产环境变量示例
├── docker-compose.yml        # Docker Compose配置
├── docker-entrypoint.sh      # Docker启动脚本
├── Dockerfile                # Docker镜像构建文件
├── index.html                # HTML模板
├── nginx.conf                # Nginx配置文件
├── package.json              # 项目依赖和脚本
├── vite.config.js            # Vite配置文件
└── README.md                 # 项目说明文档
```

## 技术栈

- **核心框架**: Vue 3 (Composition API)
- **构建工具**: Vite 4.4.0
- **UI组件库**: Element Plus 2.3.8
- **HTTP客户端**: Axios 1.4.0
- **图标库**: @element-plus/icons-vue 2.3.1
- **实时通信**: Server-Sent Events (SSE)
- **Web服务器**: Nginx (生产环境)
- **容器化**: Docker
- **开发工具**:
  - @vitejs/plugin-vue 4.2.3 (Vue插件)
  - cross-env 7.0.3 (跨平台环境变量)

## 使用说明

### 本地部署说明

#### 环境要求

- Node.js 14.0.0+
- npm 6.0.0+
- 后端服务 (字符画转换器后端)

#### 安装步骤

1. **克隆项目到本地**

   ```bash
   git clone https://github.com/yourusername/char-art-converter.git
   cd char-art-converter/frontend
   ```

2. **安装依赖**

   ```bash
   npm install
   ```

3. **配置环境变量**

   创建环境变量文件（可选）：

   ```bash
   # 复制示例文件
   cp .env.example .env
   cp .env.development.example .env.development
   cp .env.production.example .env.production
   ```

4. **开发模式运行**

   ```bash
   npm run dev
   ```

   应用将在 <http://localhost:5174> 运行

5. **构建生产版本**

   ```bash
   # 使用默认配置构建
   npm run build
   
   # 使用自定义资源路径前缀构建
   npm run build -- --base=/char-art/
   ```

   构建后的文件将位于 `dist` 目录中

6. **预览生产构建**

   ```bash
   npm run preview
   ```

#### 注意事项

- **后端服务依赖**：前端应用需要后端服务提供API支持，请确保后端服务已启动并可访问
- **API代理配置**：开发环境下，Vite会自动代理API请求到后端服务
- **文件上传限制**：默认支持最大10MB的文件上传，可通过环境变量配置
- **浏览器兼容性**：建议使用最新版本的Chrome、Firefox、Safari或Edge浏览器
- **大文件处理**：当字符画图片大小超过300MB时，由于浏览器限制，图片将不会在界面上显示，但仍可以正常下载

### Docker部署说明

如需了解如何使用Docker部署本服务，请参阅 [Docker部署指南](./Docker.md)。

Docker部署提供了更简便的部署方式，包含了Nginx Web服务器配置，支持生产环境部署。

### 配置文件说明

前端应用使用Vite的环境变量系统进行配置，支持以下配置文件：

#### 环境变量文件

- `.env` - 所有环境都会加载的默认环境变量
- `.env.development` - 开发环境特定的环境变量
- `.env.production` - 生产环境特定的环境变量

#### 主要配置项

##### 通用环境变量

```properties
# 应用名称
VITE_APP_TITLE=字符画转换器

# 应用版本
VITE_APP_VERSION=1.0.0

# API基础路径
VITE_API_BASE_PATH=/api

# 上传文件最大大小（单位：MB）
VITE_MAX_UPLOAD_SIZE=10
```

##### 开发环境变量

```properties
# API服务器地址
VITE_API_URL=http://localhost:8080

# 资源路径前缀
VITE_BASE_PATH=

# 开发服务器端口
VITE_PORT=5174

# 是否启用调试模式
VITE_DEBUG=true
```

##### 生产环境变量

```properties
# API服务器地址
VITE_API_URL=http://localhost:8080

# 资源路径前缀
VITE_BASE_PATH=char-art

# 是否启用调试模式
VITE_DEBUG=false

# 是否启用源码映射
VITE_SOURCEMAP=false
```

#### Vite配置文件

`vite.config.js` 文件包含了构建和开发服务器的配置：

- **开发服务器配置**：默认端口5174，支持API代理
- **构建配置**：支持自定义资源路径前缀
- **插件配置**：Vue 3支持
- **环境变量处理**：自动加载和处理环境变量

#### API代理配置

开发环境下，Vite配置了API代理，将前端的API请求转发到后端服务：

``` text
proxy: {
  [apiBasePath]: {
    target: 'http://localhost:8080', // 后端API服务器地址
    changeOrigin: true,
    ws: true, // 支持WebSocket和SSE
    rewrite: (path) => path
  }
}
```

#### 功能特性配置

- **文件上传**：支持JPG、PNG、JPEG、GIF、WEBP、BMP格式
- **实时进度**：使用SSE技术监控转换进度
- **响应式设计**：适配不同屏幕尺寸
- **导出功能**：支持导出为文本文件和图片文件

## 许可证

[MIT License](../LICENSE)
