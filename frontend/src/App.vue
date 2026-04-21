<template>
  <div class="arena-shell">
    <el-container class="arena-layout">
      <el-aside class="arena-sidebar" width="248px">
        <div class="brand-block">
          <p class="brand-subtitle">Kards Arena Agent</p>
          <h2>竞技场选牌助手</h2>
        </div>

        <el-card class="tip-card" shadow="never">
          <div class="tip-head">当前状态</div>
          <div class="tip-rate">{{ statusText }}</div>
          <p class="tip-desc">
            {{ statusDescription }}
          </p>
        </el-card>

        <el-card class="tip-card secondary-card" shadow="never">
          <div class="tip-head">本局进度</div>
          <div class="tip-rate">{{ pickedCards.length }}</div>
          <p class="tip-desc">
            已确认 {{ pickedCards.length }} 张，本轮为第 {{ currentPickNo }} 手。
          </p>
        </el-card>
      </el-aside>

      <el-main class="arena-main">
        <section class="hero-panel float-up">
          <div>
            <el-tag type="warning" effect="dark" round>Live Draft Assistant</el-tag>
            <h1>上传截图，分析后直接确认选牌</h1>
            <p>
              现在前端不只展示推荐，还可以直接确认“这轮到底选哪张牌”，并把结果同步到后端 session。
            </p>
          </div>
          <div class="hero-actions">
            <el-button
              type="primary"
              size="large"
              round
              :loading="loading"
              :disabled="!selectedFile"
              @click="analyzeScreenshot"
            >
              <el-icon><MagicStick /></el-icon>
              开始识别
            </el-button>
          </div>
        </section>

        <el-card class="glass-card float-up delay-1" shadow="never">
          <template #header>
            <div class="card-header">
              <span>1. 上传竞技场截图</span>
              <el-tag effect="plain">{{ selectedFile ? selectedFile.name : '等待上传' }}</el-tag>
            </div>
          </template>

          <el-upload
            class="upload-box"
            drag
            action="#"
            :auto-upload="false"
            :limit="1"
            :show-file-list="true"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :before-upload="() => false"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">点击或拖拽图片到这里</div>
            <template #tip>
              <div class="el-upload__tip">支持 png / jpg / jpeg，建议上传清晰截图</div>
            </template>
          </el-upload>

          <el-alert
            v-if="errorMessage"
            class="panel-alert"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
          />
          <el-alert
            v-if="activeAnalyzeJobId"
            class="panel-alert"
            :title="`识别任务已提交：${activeAnalyzeJobId}`"
            type="info"
            show-icon
            :closable="false"
          />
        </el-card>

        <el-row :gutter="18" class="content-row">
          <el-col :xs="24" :lg="16">
            <el-card class="glass-card float-up delay-2" shadow="never">
              <template #header>
                <div class="card-header">
                  <span>2. 识别到的候选卡牌</span>
                  <el-tag type="success">{{ displayCards.length }} 张候选</el-tag>
                </div>
              </template>

              <el-empty
                v-if="displayCards.length === 0"
                description="上传截图并完成识别后，这里会显示真实候选卡牌。"
              />

              <div v-else class="candidate-grid">
                <el-card
                  v-for="(card, index) in displayCards"
                  :key="card.id || `${card.name}-${index}`"
                  class="candidate-item"
                  shadow="hover"
                >
                  <div class="card-banner">
                    <el-tag size="small" effect="dark">{{ formatCardType(card.type) }}</el-tag>
                    <span class="card-cost">{{ card.cost ?? '-' }} 费</span>
                  </div>

                  <div class="candidate-highlights">
                    <el-tag
                      v-if="isRecommendedCard(card)"
                      type="danger"
                      effect="light"
                      round
                    >
                      AI 推荐
                    </el-tag>
                    <el-tag
                      v-if="isPickedCard(card)"
                      type="success"
                      effect="light"
                      round
                    >
                      已确认选择
                    </el-tag>
                  </div>

                  <h3>{{ card.name || '未识别卡名' }}</h3>
                  <p class="card-meta">{{ card.nation || '未知阵营' }} · {{ card.count || 1 }} 张</p>
                  <p class="card-desc">{{ card.description || '后端暂未返回卡牌描述。' }}</p>

                  <div class="candidate-actions">
                    <el-button
                      type="primary"
                      round
                      :plain="!isRecommendedCard(card)"
                      :loading="picking && activePickName === card.name"
                      :disabled="pickActionDisabled(card)"
                      @click="pickCandidate(card)"
                    >
                      {{ pickActionText(card) }}
                    </el-button>
                  </div>
                </el-card>
              </div>
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="8">
            <div class="side-column">
              <el-card class="result-card float-up delay-3" shadow="never">
                <template #header>
                  <div class="card-header">
                    <span>3. AI 推荐结果</span>
                    <el-tag type="danger" effect="dark">Top Pick</el-tag>
                  </div>
                </template>

                <el-empty
                  v-if="!recommendation"
                  description="完成一次识别后，这里会展示后端返回的推荐结论。"
                />

                <template v-else>
                  <div class="pick-name">#1 {{ recommendation.cardName }}</div>
                  <div class="pick-score">综合分 {{ recommendation.finalScoreText }}</div>
                  <el-progress :percentage="recommendation.percentage" status="exception" />

                  <el-divider />

                  <div class="result-meta">
                    <span>基础分</span>
                    <strong>{{ recommendation.baseScoreText }}</strong>
                  </div>
                  <div class="result-meta">
                    <span>来源</span>
                    <strong>{{ recommendation.sourceText }}</strong>
                  </div>
                  <div class="result-meta">
                    <span>决策方式</span>
                    <strong>{{ recommendation.decisionSourceText }}</strong>
                  </div>

                  <el-divider />

                  <p class="reason-text">{{ recommendation.reason }}</p>

                  <el-button
                    class="recommend-btn"
                    type="danger"
                    round
                    :loading="picking && activePickName === recommendation.cardName"
                    :disabled="!canPickCurrentRound || !recommendedCardForPick"
                    @click="pickCandidate(recommendedCardForPick)"
                  >
                    一键采用推荐
                  </el-button>
                </template>
              </el-card>

              <el-card class="glass-card float-up delay-3" shadow="never">
                <template #header>
                  <div class="card-header">
                    <span>4. 当前卡组状态</span>
                    <el-tag effect="plain">第 {{ currentPickNo }} 手</el-tag>
                  </div>
                </template>

                <el-empty
                  v-if="!deckState"
                  description="确认选牌后，这里会根据已选卡池更新费用曲线与风格标签。"
                />

                <template v-else>
                  <div class="deck-metrics">
                    <div class="metric-item">
                      <span>已选张数</span>
                      <strong>{{ deckState.totalCards }}</strong>
                    </div>
                    <div class="metric-item">
                      <span>单位 / 指令</span>
                      <strong>{{ deckState.unitCount }} / {{ deckState.orderCount }}</strong>
                    </div>
                    <div class="metric-item">
                      <span>前 / 中 / 后期</span>
                      <strong>{{ deckState.earlyCount }} / {{ deckState.midCount }} / {{ deckState.lateCount }}</strong>
                    </div>
                  </div>

                  <div class="deck-tags">
                    <el-tag
                      v-for="tag in deckState.tags || []"
                      :key="tag"
                      effect="light"
                      round
                    >
                      {{ tag }}
                    </el-tag>
                  </div>
                </template>
              </el-card>
            </div>
          </el-col>
        </el-row>

        <el-card class="glass-card float-up delay-3 picked-card-panel" shadow="never">
          <template #header>
            <div class="card-header">
              <span>5. 已选卡池</span>
              <el-tag effect="plain">共 {{ pickedCards.length }} 张</el-tag>
            </div>
          </template>

          <el-empty
            v-if="pickedCards.length === 0"
            description="确认一次选牌后，这里会展示本局已选卡池。"
          />

          <div v-else class="picked-grid">
            <div
              v-for="(card, index) in pickedCards"
              :key="card.id || `${card.name}-${index}`"
              class="picked-item"
            >
              <span class="picked-name">{{ card.name || '未识别卡名' }}</span>
              <span class="picked-meta">{{ card.cost ?? '-' }} 费 · {{ card.count || 1 }} 张</span>
            </div>
          </div>
        </el-card>

        <el-card class="glass-card float-up delay-3" shadow="never">
          <template #header>
            <div class="card-header">
              <span>最近识别记录</span>
              <el-tag effect="plain">共 {{ history.length }} 条</el-tag>
            </div>
          </template>

          <el-table :data="history" stripe>
            <el-table-column prop="time" label="时间" min-width="170" />
            <el-table-column prop="options" label="候选卡牌" min-width="320" />
            <el-table-column prop="recommended" label="推荐" min-width="150" />
            <el-table-column prop="picked" label="实选" min-width="150" />
            <el-table-column label="状态" min-width="110">
              <template #default="scope">
                <el-tag :type="isPendingStatus(scope.row.status) ? 'warning' : 'success'" effect="light">
                  {{ formatHistoryStatus(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, UploadFilled } from '@element-plus/icons-vue'

const SESSION_STORAGE_KEY = 'arena-agent-session-id'

const selectedFile = ref(null)
const loading = ref(false)
const picking = ref(false)
const activePickName = ref('')
const errorMessage = ref('')
const cards = ref([])
const decision = ref(null)
const history = ref([])
const sessionId = ref('')
const sessionData = ref(null)
const activeAnalyzeJobId = ref('')

let analyzePollTimer = null
let analyzeSocket = null
let analyzeJobSettled = false

const latestHistoryEntry = computed(() => {
  const currentHistory = sessionData.value?.history
  return Array.isArray(currentHistory) && currentHistory.length > 0 ? currentHistory[0] : null
})

const pendingHistoryEntry = computed(() => {
  return isPendingStatus(latestHistoryEntry.value?.status) ? latestHistoryEntry.value : null
})

const displayCards = computed(() => {
  if (cards.value.length > 0) {
    return cards.value
  }

  const pendingCards = pendingHistoryEntry.value?.offeredCards
  return Array.isArray(pendingCards) ? pendingCards : []
})

const hasLiveAnalyzeResult = computed(() => {
  return cards.value.length > 0 && Boolean(decision.value)
})

const currentPickNo = computed(() => {
  return Number(sessionData.value?.currentPickNo ?? 1)
})

const pickedCards = computed(() => {
  const currentPickedCards = sessionData.value?.pickedCards
  return Array.isArray(currentPickedCards) ? currentPickedCards : []
})

const deckState = computed(() => {
  return sessionData.value?.deckState ?? null
})

const canPickCurrentRound = computed(() => {
  if (pendingHistoryEntry.value && displayCards.value.length > 0) {
    return true
  }

  return hasLiveAnalyzeResult.value
})

const recommendation = computed(() => {
  const currentDecision = decision.value
  const recommendedCard = currentDecision?.recommendedCard
  const card = recommendedCard?.card

  if (currentDecision && recommendedCard && card) {
    const finalScore = Number(currentDecision.finalScore ?? 0)
    const baseScore = Number(recommendedCard.baseScore ?? 0)

    return {
      cardName: card.name || '未识别卡名',
      finalScoreText: finalScore.toFixed(2),
      baseScoreText: baseScore.toFixed(2),
      sourceText: recommendedCard.source || '未知来源',
      decisionSourceText: currentDecision.decisionSource || '未知',
      reason: currentDecision.llmReason || recommendedCard.comment || '后端未返回推荐理由。',
      percentage: Math.max(0, Math.min(100, Math.round(finalScore * 10)))
    }
  }

  if (pendingHistoryEntry.value?.recommendedCard) {
    const finalScore = Number(pendingHistoryEntry.value.finalScore ?? 0)
    return {
      cardName: pendingHistoryEntry.value.recommendedCard.name || '未识别卡名',
      finalScoreText: finalScore.toFixed(2),
      baseScoreText: '-',
      sourceText: '会话恢复',
      decisionSourceText: pendingHistoryEntry.value.decisionSource || '未知',
      reason: pendingHistoryEntry.value.reason || '后端未返回推荐理由。',
      percentage: Math.max(0, Math.min(100, Math.round(finalScore * 10)))
    }
  }

  return null
})

const recommendedCardForPick = computed(() => {
  if (!recommendation.value) {
    return null
  }

  return displayCards.value.find((card) => isSameCard(card, recommendation.value.cardName)) ?? null
})

const statusText = computed(() => {
  if (picking.value) {
    return '确认中'
  }

  if (loading.value) {
    return '识别中'
  }

  if (canPickCurrentRound.value) {
    return '待选牌'
  }

  if (pickedCards.value.length > 0) {
    return `已选 ${pickedCards.value.length} 张`
  }

  if (sessionId.value) {
    return '会话已创建'
  }

  return '初始化中'
})

const statusDescription = computed(() => {
  if (canPickCurrentRound.value) {
    return '这一轮已经分析完成，你可以直接点击候选卡确认实际选择。'
  }

  if (pickedCards.value.length > 0) {
    return '当前会话已记录实际选牌结果，历史和已选卡池都来自后端 session。'
  }

  return '上传截图后会先做 OCR 与推荐，再由你确认本轮最终选牌。'
})

onMounted(async () => {
  try {
    const savedSessionId = window.localStorage.getItem(SESSION_STORAGE_KEY)
    if (savedSessionId) {
      sessionId.value = savedSessionId
      try {
        await loadSessionData()
        return
      } catch (error) {
        console.warn('Stored session is no longer available, creating a new one.', error)
        sessionId.value = ''
        history.value = []
        sessionData.value = null
        window.localStorage.removeItem(SESSION_STORAGE_KEY)
      }
    }

    await ensureSession()
    await loadSessionData()
  } catch (error) {
    console.error(error)
    const message = error instanceof Error ? error.message : '初始化会话失败'
    errorMessage.value = message
  }
})

onBeforeUnmount(() => {
  clearAnalyzeWatchers()
})

function handleFileChange(uploadFile) {
  selectedFile.value = uploadFile.raw ?? null
  errorMessage.value = ''
}

function handleFileRemove() {
  selectedFile.value = null
}

function formatCardType(type) {
  if (!type) {
    return '未知'
  }

  const normalized = String(type).toLowerCase()
  if (normalized === 'unit') {
    return '单位'
  }
  if (normalized === 'order') {
    return '指令'
  }

  return type
}

function isPendingStatus(status) {
  return status === '待确认' || status === 'PENDING_CONFIRMATION'
}

function formatHistoryStatus(status) {
  if (status === 'PENDING_CONFIRMATION') {
    return '待确认'
  }

  if (status === 'CONFIRMED') {
    return '已确认'
  }

  return status || '已完成'
}

function isSameCard(card, candidateName) {
  if (!card || !candidateName) {
    return false
  }

  return String(card.name ?? '').trim() === String(candidateName).trim()
}

function isRecommendedCard(card) {
  return recommendation.value ? isSameCard(card, recommendation.value.cardName) : false
}

function isPickedCard(card) {
  const pickedCardName = pendingHistoryEntry.value?.pickedCard?.name
  return pickedCardName ? isSameCard(card, pickedCardName) : false
}

function pickActionDisabled(card) {
  if (!canPickCurrentRound.value) {
    return true
  }

  if (isPickedCard(card)) {
    return true
  }

  return picking.value && activePickName.value !== card.name
}

function pickActionText(card) {
  if (isPickedCard(card)) {
    return '已确认选择'
  }

  if (!canPickCurrentRound.value) {
    return '等待下一轮'
  }

  if (isRecommendedCard(card)) {
    return '采用推荐'
  }

  return '选这张'
}

function applySessionData(session) {
  sessionData.value = session

  const sessionHistory = Array.isArray(session?.history) ? session.history : []
  history.value = sessionHistory.map((entry) => ({
    time: entry.analyzedAt || '-',
    options: Array.isArray(entry.offeredCards)
      ? entry.offeredCards.map((card) => card?.name || '未识别').join(' / ')
      : '-',
    recommended: entry.recommendedCard?.name || '无推荐',
    picked: entry.pickedCard?.name || '-',
    status: entry.status || '已完成'
  }))
}

async function ensureSession() {
  if (sessionId.value) {
    return sessionId.value
  }

  const response = await fetch('/api/arena/start', {
    method: 'POST'
  })

  if (!response.ok) {
    throw new Error(`创建会话失败，状态码 ${response.status}`)
  }

  const result = await response.json()
  sessionId.value = result.sessionId || ''

  if (!sessionId.value) {
    throw new Error('后端没有返回有效的 sessionId')
  }

  window.localStorage.setItem(SESSION_STORAGE_KEY, sessionId.value)
  return sessionId.value
}

async function loadSessionData() {
  if (!sessionId.value) {
    history.value = []
    sessionData.value = null
    return
  }

  const response = await fetch(`/api/arena/session/${sessionId.value}`)

  if (!response.ok) {
    throw new Error(`读取会话失败，状态码 ${response.status}`)
  }

  const result = await response.json()
  applySessionData(result)
}

async function analyzeScreenshot() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择一张竞技场截图。')
    return
  }

  if (canPickCurrentRound.value) {
    ElMessage.warning('请先确认当前这一轮实际选择，再继续识别下一张截图。')
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const currentSessionId = await ensureSession()
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('sessionId', currentSessionId)

    const response = await fetch('/api/arena/analyze/async', {
      method: 'POST',
      body: formData
    })

    if (!response.ok) {
      throw new Error(`请求失败，状态码 ${response.status}`)
    }

    const result = await response.json()
    if (!result.jobId) {
      throw new Error('后端没有返回有效的识别任务 ID')
    }

    startAnalyzeWatchers(result.jobId)
    ElMessage.info('识别任务已提交，正在等待 OCR-Service 返回结果。')
  } catch (error) {
    console.error(error)
    const message = error instanceof Error ? error.message : '识别失败，请检查后端和 OCR 服务是否已启动。'
    errorMessage.value = message
    ElMessage.error(message)
    loading.value = false
    clearAnalyzeWatchers()
  }
}

function startAnalyzeWatchers(jobId) {
  clearAnalyzeWatchers()
  activeAnalyzeJobId.value = jobId
  analyzeJobSettled = false
  connectAnalyzeWebSocket(jobId)
  startAnalyzePolling(jobId)
}

function clearAnalyzeWatchers() {
  activeAnalyzeJobId.value = ''

  if (analyzePollTimer) {
    window.clearInterval(analyzePollTimer)
    analyzePollTimer = null
  }

  if (analyzeSocket) {
    analyzeSocket.close()
    analyzeSocket = null
  }
}

function connectAnalyzeWebSocket(jobId) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/arena/analyze/${jobId}`
  const socket = new WebSocket(wsUrl)
  analyzeSocket = socket

  socket.onmessage = (event) => {
    try {
      applyAnalyzeJobUpdate(JSON.parse(event.data))
    } catch (error) {
      console.warn('Invalid analyze websocket payload', error)
    }
  }

  socket.onerror = () => {
    console.warn('Analyze websocket failed, polling will continue.')
  }

  socket.onclose = () => {
    if (analyzeSocket === socket) {
      analyzeSocket = null
    }
  }
}

function startAnalyzePolling(jobId) {
  pollAnalyzeJob(jobId)
  analyzePollTimer = window.setInterval(() => {
    pollAnalyzeJob(jobId)
  }, 1200)
}

async function pollAnalyzeJob(jobId) {
  if (analyzeJobSettled || activeAnalyzeJobId.value !== jobId) {
    return
  }

  try {
    const response = await fetch(`/api/arena/analyze/jobs/${jobId}`)
    if (!response.ok) {
      throw new Error(`查询识别任务失败，状态码 ${response.status}`)
    }
    const result = await response.json()
    await applyAnalyzeJobUpdate(result)
  } catch (error) {
    console.warn(error)
  }
}

async function applyAnalyzeJobUpdate(job) {
  if (!job || job.jobId !== activeAnalyzeJobId.value || analyzeJobSettled) {
    return
  }

  if (job.status === 'QUEUED' || job.status === 'ANALYZING') {
    return
  }

  analyzeJobSettled = true
  clearAnalyzeWatchers()
  activeAnalyzeJobId.value = ''
  loading.value = false

  if (job.status === 'COMPLETED') {
    const result = job.result ?? {}
    cards.value = Array.isArray(result.offeredCards) ? result.offeredCards : []
    decision.value = result.decision ?? null
    await loadSessionData()
    ElMessage.success('识别完成，现在可以在候选卡中确认本轮实际选择。')
    return
  }

  const message = job.errorMessage || '识别任务失败，请检查 RabbitMQ、后端和 OCR worker。'
  errorMessage.value = message
  ElMessage.error(message)
}

async function pickCandidate(card) {
  if (!card || !sessionId.value) {
    return
  }

  if (!canPickCurrentRound.value) {
    ElMessage.warning('当前没有待确认的选牌结果。')
    return
  }

  picking.value = true
  activePickName.value = card.name || ''
  errorMessage.value = ''

  try {
    const response = await fetch('/api/arena/pick', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sessionId: sessionId.value,
        pickedCard: card
      })
    })

    if (!response.ok) {
      throw new Error(`确认选牌失败，状态码 ${response.status}`)
    }

    const result = await response.json()
    applySessionData(result)
    cards.value = []
    decision.value = null

    ElMessage.success(`已确认本轮选择：${card.name || '未识别卡牌'}`)
  } catch (error) {
    console.error(error)
    const message = error instanceof Error ? error.message : '确认选牌失败，请稍后重试。'
    errorMessage.value = message
    ElMessage.error(message)
  } finally {
    picking.value = false
    activePickName.value = ''
  }
}
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

.tip-card {
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.14), rgba(110, 231, 183, 0.08));
  color: #fff;
  border-radius: 16px;
}

.secondary-card {
  margin-top: auto;
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
  line-height: 1.6;
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
  gap: 12px;
}

.upload-box {
  width: 100%;
}

.upload-icon {
  font-size: 34px;
}

.panel-alert {
  margin-top: 16px;
}

.content-row {
  margin: 0 0 18px;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.candidate-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.candidate-item {
  border-radius: 16px;
  border: 1px solid rgba(17, 24, 39, 0.08);
  display: flex;
  flex-direction: column;
}

.card-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.candidate-highlights {
  min-height: 30px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
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

.card-meta {
  margin: 0 0 8px;
  color: #4b5563;
  font-size: 13px;
}

.card-desc {
  margin: 0;
  min-height: 58px;
  color: #5f6f86;
  font-size: 13px;
  line-height: 1.5;
}

.candidate-actions {
  margin-top: auto;
  padding-top: 16px;
}

.candidate-actions :deep(.el-button) {
  width: 100%;
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

.result-meta,
.metric-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  color: #4b5563;
  margin-bottom: 8px;
}

.reason-text {
  margin: 0;
  color: #334155;
  line-height: 1.7;
  white-space: pre-wrap;
}

.recommend-btn {
  width: 100%;
  margin-top: 16px;
}

.deck-metrics {
  margin-bottom: 14px;
}

.deck-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.picked-card-panel {
  margin-bottom: 18px;
}

.picked-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.picked-item {
  border: 1px solid rgba(17, 24, 39, 0.08);
  border-radius: 14px;
  padding: 14px;
  background: rgba(248, 250, 252, 0.75);
}

.picked-name {
  display: block;
  font-weight: 700;
  margin-bottom: 6px;
}

.picked-meta {
  color: #64748b;
  font-size: 13px;
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

  .candidate-grid,
  .picked-grid {
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

  .candidate-grid,
  .picked-grid {
    grid-template-columns: 1fr;
  }
}
</style>
