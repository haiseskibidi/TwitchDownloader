<script setup lang="ts">
import { useTwitchStore } from '../store/twitchStore'

const store = useTwitchStore()

const formatDate = (dateString: string) => {
  if (!dateString) return '-'
  const d = new Date(dateString)
  return d.toLocaleString()
}

const handleStop = (id: number) => {
  if (confirm('Forcibly stop recording this stream? The video recorded so far will be saved.')) {
    store.stopRecording(id)
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
        </div>
        <div class="download-actions">
          <button 
            class="btn btn-danger btn-small" 
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
