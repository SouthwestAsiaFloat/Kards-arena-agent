<template>
  <div class="arena-shell">
    <el-container class="arena-layout">
      <el-aside class="arena-sidebar" width="248px">
        <div class="brand-block">
          <p class="brand-subtitle">Kards Arena Agent</p>
          <h2>扎扎师选牌助手</h2>
        </div>
      </el-aside>

      <el-main class="arena-main">
        <section class="hero-panel float-up">
          <div>
            <el-tag type="warning" effect="dark" round>Live Draft Assistant</el-tag>
            <h1>竞技场选牌助手</h1>
            <p>上传截图，识别三张卡牌并给出推荐选择，支持快速分析与历史回看。</p>
          </div>
          <div class="hero-actions">
            <el-button type="primary" size="large" round>
              <el-icon><MagicStick /></el-icon>
              开始识别
            </el-button>

          </div>
        </section>

        <el-card class="glass-card float-up delay-1" shadow="never">
          <template #header>
            <div class="card-header">
              <span>1. 上传竞技场截图</span>
              <el-tag effect="plain">OCR 待接入</el-tag>
            </div>
          </template>
          <el-upload class="upload-box" drag action="#" :auto-upload="false" multiple>
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">点击或拖拽图片到这里</div>
            <template #tip>
              <div class="el-upload__tip">支持 png / jpg / jpeg，单文件大小不超过 10MB</div>
            </template>
          </el-upload>
        </el-card>

        <el-row :gutter="18" class="content-row">
          <el-col :xs="24" :lg="16">
            <el-card class="glass-card float-up delay-2" shadow="never">
              <template #header>
                <div class="card-header">
                  <span>2. 识别到的候选卡牌</span>
                  <el-tag type="success">{{ cards.length }} 张候选</el-tag>
                </div>
              </template>

              <div class="candidate-grid">
                <el-card
                  v-for="card in cards"
                  :key="card.id"
                  class="candidate-item"
                  shadow="hover"
                >
                  <div class="card-banner">
                    <el-tag size="small" effect="dark">{{ card.type }}</el-tag>
                    <span class="card-cost">{{ card.cost }} 费</span>
                  </div>
                  <h3>{{ card.name }}</h3>
                  <p class="card-desc">{{ card.desc }}</p>
                  <div class="score-row">
                    <span>基础评分</span>
                    <strong>{{ card.score }}</strong>
                  </div>
                  <el-progress :percentage="Math.round(card.score * 10)" :show-text="false" :stroke-width="7" />
                </el-card>
              </div>
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="8">
            <el-card class="result-card float-up delay-3" shadow="never">
              <template #header>
                <div class="card-header">
                  <span>3. AI 推荐结果</span>
                  <el-tag type="danger" effect="dark">Top Pick</el-tag>
                </div>
              </template>

              <div class="pick-name">#1 {{ recommendation.name }}</div>
              <div class="pick-score">综合分 {{ recommendation.totalScore }}</div>
              <el-progress :percentage="Math.round(recommendation.totalScore * 10)" status="exception" />

              <el-divider />

              <el-timeline>
                <el-timeline-item
                  v-for="(reason, index) in recommendation.reasons"
                  :key="index"
                  :type="index === 0 ? 'primary' : 'success'"
                >
                  {{ reason }}
                </el-timeline-item>
              </el-timeline>

              <el-button type="danger" plain round class="detail-btn">查看详细分析</el-button>
            </el-card>
          </el-col>
        </el-row>

        <el-card class="glass-card float-up delay-3" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最近识别记录</span>
              <el-tag effect="plain">共 {{ history.length }} 条</el-tag>
            </div>
          </template>

          <el-table :data="history" stripe>
            <el-table-column prop="time" label="时间" min-width="170" />
            <el-table-column prop="options" label="候选卡牌" min-width="300" />
            <el-table-column prop="pick" label="推荐" min-width="120" />
            <el-table-column label="状态" min-width="100">
              <template #default="scope">
                <el-tag type="success" effect="light">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import {
  Clock,
  DataAnalysis,
  Guide,
  MagicStick,
  Setting,
  UploadFilled
} from '@element-plus/icons-vue'

const activeMenu = ref('draft')

const cards = ref([
  {
    id: 1,
    name: '步兵突击队',
    cost: 2,
    type: '单位',
    score: 7.8,
    desc: '低费站场稳定，适合前期抢节奏。'
  },
  {
    id: 2,
    name: '重炮支援',
    cost: 5,
    type: '指令',
    score: 8.6,
    desc: '中后期清场能力强，适配控制体系。'
  },
  {
    id: 3,
    name: '侦察车',
    cost: 3,
    type: '单位',
    score: 7.1,
    desc: '机动能力优秀，可补侦察与交换。'
  }
])

const recommendation = ref({
  name: '重炮支援',
  totalScore: 8.9,
  reasons: [
    '当前曲线中后期强度不足，这张卡能补高费质量点。',
    '与已有控制型卡牌配合更强，泛用性高。',
    '单卡评分和体系适配都更优。'
  ]
})

const history = ref([
  {
    time: '2026-03-26 19:30',
    options: '步兵突击队 / 重炮支援 / 侦察车',
    pick: '重炮支援',
    status: '已完成'
  },
  {
    time: '2026-03-26 18:55',
    options: '防空炮 / 野战补给 / 炮兵阵地',
    pick: '炮兵阵地',
    status: '已完成'
  },
  {
    time: '2026-03-26 17:41',
    options: '突击工兵 / 空降分队 / 快速部署',
    pick: '空降分队',
    status: '已完成'
  }
])
</script>

<style scoped>
.arena-shell {
  min-height: 100vh;
  background:
    radial-gradient(circle at 8% 10%, rgba(251, 191, 36, 0.26), transparent 28%),
    radial-gradient(circle at 85% 5%, rgba(14, 116, 144, 0.2), transparent 25%),
    linear-gradient(130deg, #fff9ef 0%, #f4f8ff 48%, #effaf6 100%);
  color: #172033;
  padding: 18px;
}

.arena-layout {
  min-height: calc(100vh - 36px);
  border-radius: 26px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(17, 24, 39, 0.14);
  background: rgba(252, 252, 252, 0.72);
  backdrop-filter: blur(7px);
}

.arena-sidebar {
  background: linear-gradient(176deg, #182643 0%, #101a31 46%, #273757 100%);
  color: #f1f5ff;
  padding: 24px 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.brand-block {
  margin-bottom: 8px;
}

.brand-block h2 {
  margin: 6px 0 0;
  font-size: 28px;
  line-height: 1.15;
  letter-spacing: 0.4px;
  font-family: Sora, 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
}

.brand-subtitle {
  margin: 0;
  opacity: 0.75;
  font-size: 13px;
  letter-spacing: 1.4px;
  text-transform: uppercase;
}

.arena-menu {
  border: none;
  background: transparent;
}

:deep(.arena-menu .el-menu-item) {
  color: rgba(241, 245, 255, 0.92);
  border-radius: 12px;
  margin-bottom: 8px;
}

:deep(.arena-menu .el-menu-item.is-active) {
  background: rgba(236, 253, 245, 0.18);
  color: #fff;
}

.tip-card {
  margin-top: auto;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.14), rgba(110, 231, 183, 0.08));
  color: #fff;
  border-radius: 16px;
}

.tip-head {
  font-size: 13px;
  opacity: 0.88;
}

.tip-rate {
  font-size: 30px;
  margin: 8px 0 10px;
  font-weight: 700;
}

.tip-desc {
  margin: 8px 0 0;
  font-size: 12px;
  opacity: 0.85;
}

.arena-main {
  padding: 22px;
}

.hero-panel {
  border-radius: 22px;
  padding: 24px;
  margin-bottom: 18px;
  background: linear-gradient(120deg, #fff, #f4f8ff 40%, #ebfff6 100%);
  border: 1px solid rgba(17, 24, 39, 0.08);
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
}

.hero-panel h1 {
  margin: 12px 0 8px;
  font-size: 37px;
  letter-spacing: 0.4px;
  line-height: 1.15;
  font-family: Sora, 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
}

.hero-panel p {
  margin: 0;
  color: #3f4d65;
  max-width: 650px;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.glass-card,
.result-card {
  border-radius: 20px;
  border: 1px solid rgba(17, 24, 39, 0.09);
  background: rgba(255, 255, 255, 0.78);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.upload-box {
  width: 100%;
}

.upload-icon {
  font-size: 34px;
}

.content-row {
  margin: 0 0 18px;
}

.candidate-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.candidate-item {
  border-radius: 16px;
  border: 1px solid rgba(17, 24, 39, 0.08);
}

.card-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-cost {
  font-size: 13px;
  color: #4b5563;
  font-weight: 600;
}

.candidate-item h3 {
  margin: 12px 0 8px;
  font-size: 19px;
}

.card-desc {
  margin: 0 0 12px;
  min-height: 40px;
  color: #5f6f86;
  font-size: 13px;
}

.score-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #4b5563;
  font-size: 13px;
}

.pick-name {
  font-size: 27px;
  font-weight: 700;
  margin-bottom: 6px;
  color: #b91c1c;
}

.pick-score {
  margin-bottom: 12px;
  color: #4b5563;
}

.detail-btn {
  width: 100%;
  margin-top: 6px;
}

.float-up {
  animation: floatUp 0.6s ease-out forwards;
  opacity: 0;
}

.delay-1 {
  animation-delay: 0.06s;
}

.delay-2 {
  animation-delay: 0.12s;
}

.delay-3 {
  animation-delay: 0.18s;
}

@keyframes floatUp {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1180px) {
  .hero-panel {
    flex-direction: column;
    align-items: flex-start;
  }

  .candidate-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .arena-shell {
    padding: 0;
  }

  .arena-layout {
    min-height: 100vh;
    border-radius: 0;
  }

  .arena-sidebar {
    width: 100%;
  }
}

@media (max-width: 680px) {
  .arena-main {
    padding: 14px;
  }

  .hero-panel {
    padding: 18px;
  }

  .hero-panel h1 {
    font-size: 30px;
  }

  .hero-actions {
    width: 100%;
    flex-direction: column;
  }

  .hero-actions :deep(.el-button) {
    width: 100%;
  }

  .candidate-grid {
    grid-template-columns: 1fr;
  }
}
</style>