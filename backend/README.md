# Backend

`backend` 是 Kards 竞技场选牌系统的决策中枢。

它负责：

- 暴露 `REST API`
- 管理 draft 会话
- 调用 OCR 服务识别候选卡
- 用 `LangChain4j Tool Calling` 编排分析流程
- 用规则排序做主锚点和失败兜底
- 回写历史记录、已选卡池和牌组状态
- 可选地用 `Redis + Redisson` 提供会话存储、分析去重缓存和分布式锁

## 技术栈

- Java 17
- Spring Boot 4
- LangChain4j
- DashScope 兼容 OpenAI API
- Redis / Redisson

## 目录结构

```text
src/main/java/com/southwestasiafloat/backend
├── application
│   └── service
│       └── toolcalling
├── config
├── controller
├── domain
│   ├── gateway
│   ├── model
│   └── service
├── dto
└── infrastructure
    ├── cache
    ├── client
    ├── lock
    └── repository
```

## 核心能力

### 1. 会话管理

当前支持：

- `POST /api/arena/start`
- `GET /api/arena/session/{sessionId}`
- `POST /api/arena/pick`
- `DELETE /api/arena/session/{sessionId}`

`DraftSession` 中会维护：

- `currentPickNo`
- `pickedCards`
- `history`
- `deckState`

### 2. 分析链路

`POST /api/arena/analyze` 的主流程如下：

```mermaid
flowchart LR
    A["Upload Screenshot"] --> B["DraftApplicationService"]
    B --> C["Analyze Dedup Cache"]
    C --> D["ToolCallingDraftAnalyzeService"]
    D --> E["DraftAnalyzeAgent"]
    E --> F["DraftAnalyzeToolbox"]
    F --> G["OCR Gateway"]
    F --> H["KnowledgeBaseService"]
    F --> I["RuleBasedDraftRankingService"]
```

Agent 会按约定顺序调用工具：

1. `getSessionSnapshot`
2. `extractCandidates`
3. `evaluateBaseScores`
4. `analyzeDeckState`
5. `retrieveStrategyKnowledge`
6. `getPickHistory`
7. `rankCandidates`

最终输出推荐卡名、推荐理由、决策来源和分数。

### 3. 规则兜底

当前实现不是“纯 LLM 直接拍板”。

默认策略是：

- 候选卡基础分和局部牌组状态先做规则排序
- Tool Calling 结果以规则排序为主锚点
- LLM 主要负责解释、择优和打破接近分差的平局
- 如果 Agent 返回非法结果或调用失败，直接回退到规则排序结果

### 4. 轻量知识增强

当前知识增强不是向量数据库方案，而是本地 JSONL 知识库：

- `src/main/resources/knowledge-base/**/*.jsonl`

`KnowledgeBaseService` 会按：

- tag
- keyword
- collection

做轻量排序召回，再把结果通过工具交给 Agent 参考。

### 5. Redis / Redisson 集成

当前已经支持两种运行模式：

- `in-memory`
- `redis`

当启用 `redis` profile 时，后端会使用：

- `RedissonSessionRepository`
- `RedissonSessionLockManager`
- `RedissonAnalyzeResultCache`
- `RedissonAnalyzeRequestLockManager`

覆盖以下能力：

- draft session 持久化
- 同一 session 的并发保护
- 重复截图分析结果缓存
- 同一分析 key 的并发去重

### 6. 重复截图去重

`analyze` 当前会按：

- `sessionId`
- `currentPickNo`
- `screenshot SHA-256`

生成分析 key。

如果短时间内同一轮上传相同截图：

- 不会重复跑 OCR
- 不会重复调用 LLM
- 不会重复写入历史记录

## API 说明

### `POST /api/arena/start`

返回示例：

```json
{
  "sessionId": "b5d0d9b4-4e76-4f56-aef7-0c7e0e7f5f5d",
  "message": "Draft started successfully"
}
```

### `POST /api/arena/analyze`

请求格式：`multipart/form-data`

- `file`: 当前竞技场截图
- `sessionId`: 可选，建议总是传入

示例：

```bash
curl -X POST "http://127.0.0.1:8080/api/arena/analyze" \
  -F "file=@D:/screenshots/pick-01.png" \
  -F "sessionId=b5d0d9b4-4e76-4f56-aef7-0c7e0e7f5f5d"
```

响应结构：

```json
{
  "offeredCards": [
    {
      "name": "驱敌入海",
      "nation": "Japan",
      "cost": 3,
      "type": "order",
      "count": 1,
      "description": "移除1个单位。下个友方回合开始时，将其返回手牌。"
    }
  ],
  "decision": {
    "recommendedCard": {
      "card": {
        "name": "驱敌入海"
      },
      "baseScore": 3.5,
      "adjustedScore": 3.5,
      "count": 1,
      "source": "Japan.json",
      "matched": true,
      "comment": "基础质量优秀"
    },
    "llmReason": "规则排序领先，且当前牌组需要补足稳定解牌。",
    "decisionSource": "tool-calling-rag",
    "finalScore": 3.0
  }
}
```

### `POST /api/arena/pick`

请求示例：

```json
{
  "sessionId": "b5d0d9b4-4e76-4f56-aef7-0c7e0e7f5f5d",
  "pickedCard": {
    "name": "驱敌入海",
    "nation": "Japan",
    "cost": 3,
    "type": "order",
    "count": 1,
    "description": "移除1个单位。下个友方回合开始时，将其返回手牌。"
  }
}
```

### `GET /api/arena/session/{sessionId}`

返回：

- 已选卡池
- 当前牌组状态
- 最近分析历史
- 当前手数

### `DELETE /api/arena/session/{sessionId}`

结束一局并删除该会话。

## 配置说明

主配置文件：

- `src/main/resources/application.yml`
- `src/main/resources/application-redis.yml`

### LLM 配置

```yaml
llm:
  api-key: ${DASHSCOPE_API_KEY}
  model-name: qwen3-max
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
```

### OCR 配置

```yaml
ocr:
  base-url: http://127.0.0.1:18000
  path: /ocr
```

### Session / Redis 配置

```yaml
arena:
  session:
    store-type: in-memory
    ttl: PT12H
  analysis:
    cache-ttl: PT10M
  redis:
    address: redis://127.0.0.1:6379
    database: 0
    lock-watchdog-timeout: PT30S
```

说明：

- 默认是 `in-memory`
- 当启用 `redis` profile 时，`arena.session.store-type` 会切成 `redis`

## 本地运行

### 普通模式

```powershell
$env:DASHSCOPE_API_KEY="your_api_key"
.\mvnw.cmd spring-boot:run
```

### Redis 模式

```powershell
$env:DASHSCOPE_API_KEY="your_api_key"
$env:SPRING_PROFILES_ACTIVE="redis"
.\mvnw.cmd spring-boot:run
```

默认服务地址：

- `http://127.0.0.1:8080`

## 测试与验证

主代码目前可以通过：

```powershell
.\mvnw.cmd -DskipTests compile
```

注意：

- 当前仓库里有一部分旧测试已经落后于实现，`.\mvnw.cmd test` 不是全绿
- 如果你要补完整回归，建议优先补：
  - Redis / Redisson 集成测试
  - 并发分析去重测试
  - session 生命周期测试

## 当前限制

- 知识增强目前是轻量检索，不是完整向量化 RAG
- 结果质量仍然依赖 OCR 和模型稳定性
- 未接入统一日志追踪、指标采集和效果评估面板
- 无 `sessionId` 时仍更适合单机本地开发场景

## 推荐阅读

- `application/service/DraftApplicationService.java`
- `application/service/toolcalling/ToolCallingDraftAnalyzeService.java`
- `application/service/toolcalling/DraftAnalyzeToolbox.java`
- `infrastructure/repository/RedissonSessionRepository.java`
- `infrastructure/lock/RedissonSessionLockManager.java`
