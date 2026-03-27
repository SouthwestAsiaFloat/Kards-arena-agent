
# OCR Service (Kards Arena Agent)

一个用于 **Kards 竞技场选牌辅助** 的 OCR 服务。

当前功能：
- 从截图中自动检测三张卡牌位置
- 使用 PaddleOCR 识别卡牌文本
- 检测卡牌数量（如 `2x`）
- 提取基础结构化信息（name / cost / count）
- 支持后续卡牌数据库匹配（card matcher）

---

## 🚀 功能模块

当前服务由以下核心模块组成：

```

core/
├── layout_parser.py   # 卡牌位置检测（OpenCV）
├── ocr_runner.py      # OCR封装（PaddleOCR）
├── pipeline.py        # 主流程（检测 -> OCR -> count）
├── card_parser.py     # 从OCR结果提取字段（name / cost / count）
├── card_matcher.py    # 卡牌数据库匹配（开发中）

````

---

## 🧠 整体流程

```text
截图
  ↓
layout_parser（检测三张卡位置）
  ↓
裁剪单卡
  ↓
ocr_runner（识别文本）
  ↓
card_parser（提取 name / cost / count）
  ↓
card_matcher（数据库匹配，规划中）
````

---

## 📦 项目结构

```
ocr-service/
├── app/
│   └── main.py            # FastAPI入口
├── core/
├── data/
│   └── cards.json        # 卡牌数据库（后续使用）
├── .venv/
├── requirements.txt
└── README.md
```

---

## ⚙️ 环境依赖

推荐 Python 3.10+

安装依赖：

```bash
pip install fastapi uvicorn numpy opencv-python paddleocr rapidfuzz
```

---

## ▶️ 启动服务

在项目根目录运行：

```bash
uvicorn app.main:app --reload
```

启动成功后：

```
http://127.0.0.1:8000
```

---

## 🧪 API 使用

打开接口文档：

```
http://127.0.0.1:8000/docs
```

使用 `/ocr` 接口上传截图。

---

## 📥 请求示例

```bash
curl -X POST "http://127.0.0.1:8000/ocr" \
  -F "file=@test.png"
```

---

## 📤 返回示例

```json
{
  "cards": [
    {
      "name": "复仇",
      "cost": 2,
      "count": 2,
      "raw_texts": ["2", "K", "复仇", "..."]
    },
    {
      "name": "U-16",
      "cost": 2,
      "count": 2,
      "raw_texts": ["2", "K", "北", "U16", "U-16"]
    },
    {
      "name": "四号坦克 J型",
      "cost": 4,
      "count": 1,
      "raw_texts": ["6k", "四號坦克J型十", "4", "5", "闪击"]
    }
  ]
}
```

---

## 🔍 当前能力

✅ 卡牌区域检测（鲁棒）
✅ OCR 文本识别
✅ 卡牌数量识别（2x）
⚠️ 卡名 / 费用解析仍存在 OCR 误差
🚧 卡牌数据库匹配（开发中）

---

## 🧩 后续规划

### 1. Card Matcher（核心）

* 基于数据库的模糊匹配
* Top-K 召回
* 可接入 7B 模型做 rerank（RAG）

### 2. Agent 决策

* 根据当前牌池选择最优卡牌
* 曲线优化（cost curve）
* 卡牌协同分析

### 3. 前端

* 上传截图
* 实时显示推荐选牌

---

## 🧠 技术栈

* FastAPI
* OpenCV（卡牌检测）
* PaddleOCR（文本识别）
* RapidFuzz（模糊匹配）
* （可选）LLM（7B，用于 rerank）

---

## ⚠️ 注意事项

* 当前只支持 **标准 PC 截图**
* 手机截图 / 非标准 UI 可能识别不稳定
* OCR 结果存在噪声，需要后续匹配修正

---

## 🧪 开发建议

推荐开发顺序：

1. 保证 OCR pipeline 稳定
2. 实现 `card_matcher.py`
3. 接入卡牌数据库
4. 再考虑引入大模型（7B）

---

## 📌 项目定位

本项目不是通用 OCR 系统，而是：

> **面向 Kards 竞技场的专用选牌 AI**

OCR 只是输入手段，核心价值在：

👉 卡牌识别 + 数据库匹配 + 选牌决策

---

## 👨‍💻 作者备注

当前阶段为 MVP，重点在：

* 跑通全链路
* 保持结构清晰
* 避免过度设计

后续可逐步拆分为：

* OCR Service
* Card Matcher Service
* Agent Service

---

```



