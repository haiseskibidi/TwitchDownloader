<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useTwitchStore } from '../store/twitchStore'

const store = useTwitchStore()

// State for active ticking timer
const now = ref(Date.now())
let timer: any = null

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const formatDate = (dateString: string) => {
  if (!dateString) return '-'
  const d = new Date(dateString)
  return d.toLocaleString()
}

const getDuration = (startedAt: string) => {
  if (!startedAt) return '00:00:00'
  const startTime = new Date(startedAt).getTime()
  const diffMs = now.value - startTime
  if (diffMs < 0) return '00:00:00'

  const diffSecs = Math.floor(diffMs / 1000)
  const hrs = Math.floor(diffSecs / 3600)
  const mins = Math.floor((diffSecs % 3600) / 60)
  const secs = diffSecs % 60

  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(hrs)}:${pad(mins)}:${pad(secs)}`
}

const handleStop = (id: number) => {
  if (confirm('Остановить запись этого стрима? Записанная часть будет сохранена.')) {
    store.stopRecording(id)
  }
}

const handleSplit = (id: number) => {
  if (confirm('Сохранить записанный кусок стрима? Он будет подготовлен к скачиванию на ПК, а запись трансляции автоматически продолжится в новый файл.')) {
    store.splitRecording(id)
  }
}

const handleViewLogs = (username: string) => {
  store.selectStreamerForLogs(username)
}
</script>

<template>
  <section v-if="store.recordings.some(r => r.status === 'ACTIVE')">
    <div class="section-header">
      <h2>Активные записи</h2>
    </div>
    <div class="active-downloads">
      <div v-for="r in store.recordings.filter(rec => rec.status === 'ACTIVE')" :key="r.id" class="download-item">
        <div class="download-item-header">
          <a :href="'https://twitch.tv/' + r.streamer.twitchUsername" target="_blank" rel="noopener noreferrer" class="download-title streamer-link" :title="r.title">{{ r.title }}</a>
          <span class="status-badge recording">В процессе</span>
        </div>
        <div class="download-meta">
          <span>Стример: <a :href="'https://twitch.tv/' + r.streamer.twitchUsername" target="_blank" rel="noopener noreferrer" class="streamer-link"><strong>{{ r.streamer.displayName }}</strong></a></span>
          <span>Начало: {{ formatDate(r.startedAt) }}</span>
          <span>Идет: <strong style="color: var(--accent-lavender);">{{ getDuration(r.startedAt) }}</strong></span>
        </div>
        <div class="download-actions">
          <button 
            class="btn btn-secondary btn-small"
            style="margin-right: 0.5rem;"
            @click="handleSplit(r.id)"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" style="margin-right:0.25rem">
              <!-- Scissors/Split icon path -->
              <path d="M6 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 3c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm12-3c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 3c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zM9.64 7.64c0.2,0.2 0.45,0.36 0.71,0.48l1.65,3.3 -1.65,3.3c-0.26,0.12 -0.51,0.28 -0.71,0.48 -1.07,1.07 -1.07,2.82 0,3.89 1.07,1.07 2.82,1.07 3.89,0 0.2,-0.2 0.36,-0.45 0.48,-0.71l3.3,-1.65 3.3,1.65c0.12,0.26 0.28,0.51 0.48,0.71 1.07,1.07 2.82,1.07 3.89,0 1.07,-1.07 1.07,-2.82 0,-3.89 -0.2,-0.2 -0.45,-0.36 -0.71,-0.48l-1.65,-3.3 1.65,-3.3c0.26,-0.12 0.51,-0.28 0.71,-0.48 1.07,-1.07 1.07,-2.82 0,-3.89 -1.07,-1.07 -2.82,-1.07 -3.89,0 -0.2,0.2 -0.36,0.45 -0.48,0.71L12,9.7l-3.3,-1.65c-0.12,-0.26 -0.28,-0.51 -0.48,-0.71 -1.07,-1.07 -2.82,-1.07 -3.89,0 -1.07,1.07 -1.07,2.82 0,3.89 0.2,0.2 0.45,0.36 0.71,0.48z"/>
            </svg>
            Сохранить кусок
          </button>
          <button 
            class="btn btn-danger btn-small" 
            style="margin-right: 0.5rem;"
            @click="handleStop(r.id)"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" style="margin-right:0.25rem">
              <path d="M6 6h12v12H6z"/>
            </svg>
            Остановить
          </button>
          <button 
            class="btn btn-icon btn-small"
            title="Показать логи"
            @click="handleViewLogs(r.streamer.twitchUsername)"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
              <path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 12H4v-2h8v2zm8-4H4V9.9h16V12zm0-4H4V6h16v2z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
