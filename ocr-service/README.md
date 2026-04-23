# OCR Service

`ocr-service` 是 Kards 竞技场截图识别服务。

它负责把竞技场三选一截图转换成后端可以直接消费的标准卡牌对象，重点不是做最终推荐，而是把“这张图里到底是哪 3 张卡”尽可能稳定地识别出来。

## 当前职责

服务当前负责：

- 接收竞技场截图
- 检测 3 张候选卡区域
- 用 `PaddleOCR` 提取标题、正文、费用、数量等文本
- 做文本清洗与归一化
- 用 `RapidFuzz` 在本地卡库中做模糊匹配
- 返回标准卡牌结构

一句话概括：

```text
截图 -> 区域切分 -> OCR -> 文本解析 -> 模糊匹配 -> 标准卡牌对象
```

## 技术栈

- Python 3.10+
- FastAPI
- Uvicorn
- RabbitMQ worker / Pika
- OpenCV
- PaddleOCR
- RapidFuzz

## 目录结构

```text
ocr-service/
├── app/
│   └── main.py
├── core/
│   ├── card_parser.py
│   ├── layout_parser.py
│   ├── ocr_runner.py
│   ├── pipeline.py
│   └── search_cards.py
├── data/
│   └── cards.json
├── test/
├── requirements.txt
└── README.md
```

### 关键文件

- `app/main.py`
  FastAPI 入口，提供 `/ocr`
- `core/layout_parser.py`
  负责检测三张卡的位置与子区域
- `core/ocr_runner.py`
  封装 PaddleOCR 调用和 OCR 结果排序
- `core/pipeline.py`
  串联截图切分、OCR 和中间结构输出
- `core/card_parser.py`
  负责 OCR 结果字段化和清洗
- `core/search_cards.py`
  基于 RapidFuzz 做卡牌数据库模糊匹配

## 当前接口

### `POST /ocr`

请求：

- `multipart/form-data`
- 字段名：`file`

示例：

```bash
curl -X POST "http://127.0.0.1:18000/ocr" \
  -F "file=@D:/screenshots/pick-01.png"
```

返回示例：

```json
[
  {
    "id": "xxxx",
    "seq_id": "card_0001",
    "name": "驱敌入海",
    "nation": "Japan",
    "cost": 3,
    "attack": null,
    "defense": null,
    "keywords": ["部署"],
    "description": "移除1个单位。下个友方回合开始时，将其返回手牌。",
    "type": "order",
    "count": 1
  }
]
```

## 内部流程

服务当前的处理顺序是：

1. 读取上传图片
2. 加载本地 `cards.json`
3. 识别三张卡的大致布局
4. OCR 提取每张卡的标题、正文、费用、数量文本
5. 生成结构化 OCR 中间结果
6. 清洗文本并构造模糊查询
7. 用 RapidFuzz 搜索最接近的标准卡牌
8. 返回简化后的卡牌结果

## 安装与运行

### 1. 创建虚拟环境

```powershell
python -m venv .venv
.venv\Scripts\activate
```

### 2. 安装基础依赖

```powershell
pip install -r requirements.txt
```

### 3. 补装 OCR / 模糊匹配依赖

当前仓库里的 `requirements.txt` 仍是最小基础集合，没有把下面这些依赖完整写进去，首次运行请手动补装：

```powershell
pip install rapidfuzz paddleocr paddlepaddle
```

如果你使用 GPU，请安装与本机环境匹配的 `paddlepaddle-gpu`。

### 4. 启动服务

```powershell
uvicorn app.main:app --host 127.0.0.1 --port 18000
```

服务地址：

- `http://127.0.0.1:18000`

### 5. 启动 RabbitMQ OCR worker

异步链路下，后端不会直接 HTTP 等待 OCR，而是把任务投递到 RabbitMQ。OCR worker 消费任务，识别完成后把结果发回结果队列。

```powershell
python -m app.worker
```

默认配置：

```text
RABBITMQ_URL=amqp://guest:guest@127.0.0.1:5672/%2F
OCR_MQ_EXCHANGE=arena.ocr
OCR_REQUEST_QUEUE=arena.ocr.requests
OCR_RETRY_QUEUE=arena.ocr.requests.retry
OCR_DEAD_QUEUE=arena.ocr.requests.dead
OCR_RESULT_QUEUE=arena.ocr.results
OCR_REQUEST_ROUTING_KEY=ocr.request
OCR_RETRY_ROUTING_KEY=ocr.request.retry
OCR_DEAD_ROUTING_KEY=ocr.request.dead
OCR_RESULT_ROUTING_KEY=ocr.result
OCR_WORKER_MAX_RETRIES=2
OCR_WORKER_RETRY_DELAY_SECONDS=2
```

### 6. Worker 并发与重试

当前 worker 使用 `prefetch_count=1`，也就是单个 worker 进程一次只处理一个 OCR 任务。这样做是为了让 PaddleOCR 资源占用更可控。

如果要提升吞吐，推荐横向多开 worker 进程，而不是在同一个 Python 进程里加线程池：

```powershell
python -m app.worker
python -m app.worker
python -m app.worker
```

RabbitMQ 会自动把请求队列里的任务分发给空闲 worker。

worker 的消息确认策略：

- OCR 成功并且结果成功发布到结果队列后，才会 `ack` 原始请求。
- 如果结果发布失败，会 `nack` 并重新入队，避免请求消息丢失。
- 可恢复异常会发布到 retry queue，等待 `OCR_WORKER_RETRY_DELAY_SECONDS` 后再回到请求队列。
- 超过 `OCR_WORKER_MAX_RETRIES` 后会把失败结果发给后端。
- 缺少 `jobId` 等无法关联后端 job 的坏消息会进入 dead queue。
- 图片无法解码等可关联 job 的坏请求不会反复重试，会直接返回失败结果给后端。

可以用环境变量调整重试行为：

```powershell
$env:OCR_WORKER_MAX_RETRIES="3"
$env:OCR_WORKER_RETRY_DELAY_SECONDS="1"
python -m app.worker
```

## 调试与测试

### 命令行调用接口

仓库里有一个简单的接口测试脚本：

```powershell
python test/test_parse_image.py D:/screenshots/pick-01.png
```

默认会请求：

- `http://127.0.0.1:18000/ocr`

### 单元测试

当前 `test/` 目录中有部分 OCR runner 相关测试，但也有一些旧测试还没有完全跟随当前实现更新。

如果你要继续补测试，建议优先覆盖：

- OCR 文本排序
- 区域切分
- 费用 / 数量误识别场景
- 模糊匹配命中率

## 当前已完成

- FastAPI 服务入口
- PaddleOCR 进程内复用与预热
- 三张卡区域切分
- 标题 / 正文 / 费用 / 数量的拆分识别
- 文本清洗和归一化
- 基于 `WRatio` 的模糊匹配
- 返回可直接给后端使用的标准卡牌结构

## 当前限制

- `requirements.txt` 还没完全覆盖真实依赖
- 白板卡和低质量截图仍然更难识别
- 当前匹配策略仍以字符串相似度为主
- 缺少系统化的 Top1 / Top3 指标统计
- 暂未做统一的 benchmark 和效果回归报表

## 和后端的关系

`ocr-service` 不负责最终选牌决策。

它负责解决的是：

```text
这张截图里是哪 3 张卡
```

最终的推荐与会话维护由 `backend` 负责：

- 候选卡评分
- 知识检索增强
- 推荐理由生成
- 历史记录和牌组状态维护

## 推荐后续补齐

- 把 `rapidfuzz`、`paddleocr`、`paddlepaddle` 补进 `requirements.txt`
- 增加一套稳定的示例截图测试集
- 增加 Top1 / Top3 统计脚本
- 补充 OCR 失败样本日志和调试输出
- 进一步优化白板卡与模糊文本的识别策略
