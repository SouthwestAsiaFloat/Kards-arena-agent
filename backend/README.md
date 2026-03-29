# 🧠 Backend - Kards Arena Draft Agent (Java)

本模块为 **Kards Arena Draft Agent 的核心决策服务（Agent Service）**，
基于 **Spring Boot** 实现，负责：

* 🧠 Draft 选牌决策
* 📊 牌池（Deck）状态分析
* 🔄 Draft Session 状态管理
* 🔗 调用 OCR / LLM 等外部服务

---

# 📌 模块职责

Backend（Java）是整个系统的**决策中枢**：

```text
前端 / 调用方
      ↓
Backend（本服务，Java Agent）
      ↓
OCR Service（Python）
```

---

## Backend 负责什么？

* 接收三选一卡牌（或 OCR 结果）
* 维护 Draft Session（已选牌）
* 分析当前牌池状态（Deck State）
* 调用领域规则（skills）进行评分
* 返回推荐卡牌及解释

---

## Backend 不负责什么？

* ❌ 图片识别（由 OCR-service 负责）
* ❌ 原始文本解析
* ❌ UI 展示

---

# 🧱 项目结构说明

```text
com.southwestasiafloat.backend
├─ controller
├─ dto
├─ application
├─ domain
├─ infrastructure
├─ config
└─ util
```

---

## 🔹 controller

```text
controller/
  └─ ArenaController.java
```

* 对外 HTTP 接口入口
* 接收请求、返回响应
* 不包含业务逻辑

---

## 🔹 dto（数据传输对象）

```text
dto/
├─ request/
└─ response/
```

### request

* `StartDraftRequest`：开始一局
* `DraftAnalyzeRequest`：分析三选一
* `DraftPickRequest`：确认选择

### response

* `ArenaResponse`：通用响应
* `DraftAnalyzeResponse`：推荐结果
* `StartDraftResponse`：session 初始化结果

---

## 🔹 application（应用层）

```text
application/service/
├─ DraftApplicationService
└─ DraftSessionApplicationService
```

职责：

* 流程编排（orchestration）
* 不做复杂业务计算
* 负责串联 domain 层能力

---

## 🔹 domain（领域层，核心）

```text
domain/
├─ model/
├─ service/
└─ gateway/
```

这是整个系统最重要的部分。

---

### 📦 domain.model（领域模型）

```text
Card                    # 卡牌实体
DraftSession            # 一局选牌状态
DeckState               # 当前牌池分析结果
CardEvaluationResult    # 单卡评估结果
OfferedCards            # 三选一集合
```

---

### 🧠 domain.service（核心逻辑 / skills）

```text
DraftDecisionService    # 总决策逻辑
DeckStateAnalyzer       # 牌池分析（核心）
CardEvaluationService   # 单卡评分
SynergyAnalyzer         # 协同分析
```

👉 这些就是 Agent 的“技能系统（skills）”

---

### 🔌 domain.gateway（外部依赖接口）

```text
OcrGateway
LlmGateway
SessionRepository
```

* 定义接口（不关心实现）
* 解耦 domain 和 infrastructure

---

## 🔹 infrastructure（基础设施层）

```text
infrastructure/
├─ client/
└─ repository/
```

---

### client（外部调用实现）

```text
OcrHttpClient   # 调用 OCR-service
LlmHttpClient   # 调用大模型
```

---

### repository（存储实现）

```text
InMemorySessionRepository   # DraftSession 内存存储
```

后期可以替换为：

* Redis
* MySQL

---

## 🔹 config

* Spring Boot 配置
* Bean 配置
* HTTP Client 配置等

---

## 🔹 util

* 工具类
* JSON 处理等

---

# 🔄 核心业务流程

## 1️⃣ 开始 Draft

```text
POST /draft/start
→ 创建 DraftSession
→ 返回 sessionId
```

---

## 2️⃣ 分析三选一（核心）

```text
POST /draft/analyze
```

流程：

```text
1. 获取 DraftSession
2. 分析 DeckState（DeckStateAnalyzer）
3. 对 offered_cards 逐个评估（CardEvaluationService）
4. 综合评分（DraftDecisionService）
5. 返回推荐
```

---

## 3️⃣ 选择卡牌

```text
POST /draft/pick
```

流程：

```text
1. 更新 DraftSession
2. 加入 picked_cards
3. 更新 pickNo
```

---

# 🧠 核心设计理念

## ✅ 1. 有状态 Agent（Stateful）

系统通过 `DraftSession` 维护上下文：

```text
picked_cards + pick_no → 决策依据
```

---

## ✅ 2. Deck State 驱动决策

不是选“最强卡”，而是选：

```text
对当前牌池最合适的卡
```

---

## ✅ 3. Skills 组合决策

Agent 通过多个技能完成判断：

* 单卡强度
* 曲线分析
* 协同分析
* 国家倾向
* 风险评估

---

## ✅ 4. 分层清晰

| 层              | 职责     |
| -------------- | ------ |
| controller     | HTTP入口 |
| application    | 流程编排   |
| domain         | 业务核心   |
| infrastructure | 外部依赖   |

---

# ⚙️ 运行方式

## 1. 启动项目

```bash
./mvnw spring-boot:run
```

或：

```bash
mvn spring-boot:run
```

---

## 2. 默认端口

```text
http://localhost:8080
```

---

## 3. 示例接口

### 开始 Draft

```http
POST /draft/start
```

---

### 分析三选一

```http
POST /draft/analyze
```

```json
{
  "sessionId": "abc123",
  "offered_cards": [...]
}
```

---

### 选择卡牌

```http
POST /draft/pick
```

```json
{
  "sessionId": "abc123",
  "chosen_index": 1
}
```

---

# 🚀 当前实现阶段

* ✅ DraftSession 管理
* ✅ 基础评分逻辑
* ⏳ DeckState 深度分析
* ⏳ OCR 集成
* ⏳ LLM 解释增强

---

# 📈 后续规划

* Redis Session 存储
* 更复杂协同系统
* 多策略评分引擎
* UI 可视化
* Draft Replay 分析

---

# 🧠 一句话总结

> Backend 是整个系统的“大脑”，负责在当前牌池上下文中做最优选牌决策。

---
