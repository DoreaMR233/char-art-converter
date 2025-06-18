# 字符画转换器前端环境变量配置指南

## 环境变量文件

本项目使用Vite的环境变量系统，支持以下环境变量文件：

- `.env`：所有环境都会加载的默认环境变量
- `.env.development`：开发环境特定的环境变量
- `.env.production`：生产环境特定的环境变量

## 可用的环境变量

### 通用环境变量

| 变量名 | 说明 | 默认值 |
|-------|------|-------|
| `VITE_APP_TITLE` | 应用名称 | 字符画转换器 |
| `VITE_APP_VERSION` | 应用版本 | 0.0.1 |
| `VITE_API_BASE_PATH` | API基础路径 | /api |
| `VITE_MAX_UPLOAD_SIZE` | 上传文件最大大小（MB） | 10 |

### 开发环境变量

| 变量名 | 说明 | 默认值 |
|-------|------|-------|
| `VITE_API_URL` | API服务器地址 | http://localhost:8080 |
| `VITE_BASE_PATH` | 资源路径前缀 | (空) |
| `VITE_PORT` | 开发服务器端口 | 5173 |
| `VITE_DEBUG` | 是否启用调试模式 | true |

### 生产环境变量

| 变量名 | 说明 | 默认值 |
|-------|------|-------|
| `VITE_API_URL` | API服务器地址 | http://localhost:8080 |
| `VITE_BASE_PATH` | 资源路径前缀 | char-art |
| `VITE_DEBUG` | 是否启用调试模式 | false |
| `VITE_SOURCEMAP` | 是否启用源码映射 | false |

## 使用方法

### 在开发中使用

在开发过程中，可以通过修改`.env.development`文件来配置开发环境的变量。例如：

```
# 开发环境配置
VITE_API_URL=http://localhost:3000
VITE_PORT=3000
```

### 在生产中使用

在生产环境中，可以通过修改`.env.production`文件来配置生产环境的变量。例如：

```
# 生产环境配置
VITE_API_URL=https://api.example.com
VITE_BASE_PATH=char-art
```

### 在Docker中使用

在Docker环境中，可以通过环境变量来覆盖配置文件中的设置。例如：

```bash
docker run -d \
  --name char-art-frontend \
  -p 8080:80 \
  -e VITE_API_URL=http://api.example.com \
  -e VITE_BASE_PATH=char-art \
  char-art-frontend:latest
```

## 在代码中访问环境变量

在代码中，可以通过`import.meta.env`来访问环境变量。例如：

```javascript
// 获取API URL
const apiUrl = import.meta.env.VITE_API_URL

// 获取应用标题
const appTitle = import.meta.env.VITE_APP_TITLE
```

注意：只有以`VITE_`开头的环境变量才会暴露给客户端代码。