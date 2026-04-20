# Frontend

`frontend` 是这个项目的前端工作台，基于 `Vue 3 + Vite + Element Plus`。

它不是模板页，而是已经接入后端 session、分析接口和选牌确认流程的单页应用。

## 技术栈

- Vue 3
- Vite
- Element Plus

## 当前功能

- 自动创建或恢复本地 `sessionId`
- 上传竞技场截图并触发后端分析
- 展示 OCR 识别后的候选卡
- 展示 AI 推荐结果和推荐理由
- 一键采用推荐卡，或手动确认任意候选卡
- 展示当前牌组状态
- 展示已选卡池
- 展示最近分析历史

## API 依赖

前端依赖后端提供以下接口：

- `POST /api/arena/start`
- `POST /api/arena/analyze`
- `POST /api/arena/pick`
- `GET /api/arena/session/{sessionId}`
- `DELETE /api/arena/session/{sessionId}`

开发时通过 Vite 代理转发：

```js
server: {
  proxy: {
    '/api': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true
    }
  }
}
```

也就是说：

- 前端开发服务器默认跑在 `http://127.0.0.1:5173`
- `/api/*` 请求默认转发到 `http://127.0.0.1:8080`

## 目录结构

```text
frontend/
├── src/
│   ├── App.vue
│   ├── main.js
│   ├── style.css
│   └── assets/
├── index.html
├── package.json
└── vite.config.js
```

### 关键文件

- `src/App.vue`
  前端主界面，包含上传、分析、确认选牌、历史和卡组状态逻辑
- `src/main.js`
  应用入口
- `src/style.css`
  全局样式
- `vite.config.js`
  开发代理配置

## 本地开发

### 安装依赖

```powershell
npm install
```

### 启动开发服务器

```powershell
npm run dev
```

### 打包

```powershell
npm run build
```

### 本地预览构建产物

```powershell
npm run preview
```

## 运行前提

前端要正常工作，至少需要：

1. `backend` 已启动
2. `ocr-service` 已启动
3. `backend` 能正常访问 LLM

推荐启动顺序：

1. `ocr-service`
2. `backend`
3. `frontend`

## 页面行为说明

### 会话恢复

前端会把 `sessionId` 存在 `localStorage` 中，刷新页面后会尝试恢复已有会话。

如果后端已经不存在该 session：

- 前端会清理本地缓存
- 然后重新创建新会话

### 分析流程

点击“开始识别”后，前端会：

1. 确保有有效 session
2. 以 `multipart/form-data` 上传截图
3. 获取后端推荐结果
4. 再次拉取最新 session 数据
5. 更新候选卡、历史、牌组状态和推荐结果

### 选牌流程

点击任意候选卡后，前端会：

1. 把当前选中的卡作为 `pickedCard` 回传后端
2. 让后端更新 `pickedCards` 和 `deckState`
3. 刷新当前页面的历史和卡池展示

## 当前限制

- 目前仍是手动上传截图，不支持桌面自动监听
- 页面逻辑基本集中在 `App.vue`，还没有拆成更细的组件
- 暂未接入前端自动化测试
- 当前没有独立环境变量文件，默认直接依赖 Vite 代理和后端固定地址

## 后续可以继续做的方向

- 拆分页面组件和状态管理
- 增加 loading / error / empty state 的更细粒度体验
- 增加历史回放、牌组曲线图和标签解释
- 支持配置化后端地址
- 增加前端自动化测试
