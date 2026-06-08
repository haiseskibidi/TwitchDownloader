<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useTwitchStore } from '../store/twitchStore'
import CustomSelect from '../components/CustomSelect.vue'

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

// Archive filters
const searchFilter = ref('')
const statusFilter = ref('')

const statusOptions = [
  { value: '', label: 'Все статусы' },
  { value: 'COMPLETED', label: 'Готов на сервере' },
  { value: 'MOVED_TO_LOCAL', label: 'Скачан на ПК (rclone)' },
  { value: 'DELETED', label: 'Удалён автоочисткой' },
  { value: 'FAILED', label: 'Ошибка записи' }
]

// Filtered recordings for Archive tab
const filteredRecordings = computed(() => {
  return store.recordings.filter(rec => {
    const titleMatch = rec.title.toLowerCase().includes(searchFilter.value.toLowerCase())
    const nameMatch = rec.streamer.displayName.toLowerCase().includes(searchFilter.value.toLowerCase()) || 
                      rec.streamer.twitchUsername.toLowerCase().includes(searchFilter.value.toLowerCase())
    const statusMatch = !statusFilter.value || rec.status === statusFilter.value
    
    return (titleMatch || nameMatch) && statusMatch
  })
})

// Delete recording handler
const handleDeleteRecording = async (id: number) => {
  if (confirm('Delete this record? This will permanently delete the recorded video file on the server.')) {
    await store.deleteRecording(id)
  }
}

// Helper: Format file size
const formatSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// Helper: Format date
const formatDate = (dateString: string) => {
  if (!dateString) return '-'
  const d = new Date(dateString)
  return d.toLocaleString()
}

// Helper: Format duration
const getDuration = (startedAt: string, endedAt: string, status: string) => {
  if (!startedAt) return '-'
  const start = new Date(startedAt).getTime()
  const end = (status === 'ACTIVE' || !endedAt) ? now.value : new Date(endedAt).getTime()
  const diffMs = end - start
  if (diffMs < 0) return '00:00:00'

  const diffSecs = Math.floor(diffMs / 1000)
  const hrs = Math.floor(diffSecs / 3600)
  const mins = Math.floor((diffSecs % 3600) / 60)
  const secs = diffSecs % 60

  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(hrs)}:${pad(mins)}:${pad(secs)}`
}
</script>

<template>
  <main class="archive-layout">
    <section class="glass-panel" style="overflow: visible;">
      <div class="section-header">
        <h2>Архив записей</h2>
      </div>
      
      <!-- Filter bar -->
      <div class="archive-filters" style="margin-bottom: 1.5rem; display: flex; gap: 1rem; align-items: center;">
        <input 
          type="text" 
          class="input-text" 
          style="max-width: 300px; flex: 1;" 
          v-model="searchFilter" 
          placeholder="Поиск по названию или стримеру..." 
        />
        <div style="width: 220px; flex-shrink: 0;">
          <CustomSelect 
            v-model="statusFilter" 
            :options="statusOptions" 
            placeholder="Все статусы"
          />
        </div>
      </div>

      <!-- Records list -->
      <div v-if="filteredRecordings.length === 0" class="empty-state">
        Записи не найдены.
      </div>
      <div v-else class="archive-list">
        <div v-for="rec in filteredRecordings" :key="rec.id" class="archive-item">
          <div class="archive-stream-info">
            <a :href="'https://twitch.tv/' + rec.streamer.twitchUsername" target="_blank" rel="noopener noreferrer" class="archive-title streamer-link" :title="rec.title">{{ rec.title }}</a>
            <a :href="'https://twitch.tv/' + rec.streamer.twitchUsername" target="_blank" rel="noopener noreferrer" class="archive-streamer streamer-link">@{{ rec.streamer.displayName }}</a>
          </div>
          
          <div class="archive-date">
            {{ formatDate(rec.startedAt) }}
          </div>

          <div class="archive-duration" title="Длительность записи">
            {{ getDuration(rec.startedAt, rec.endedAt, rec.status) }}
          </div>
          
          <div class="archive-size">
            {{ formatSize(rec.fileSize) }}
          </div>
          
          <div class="archive-status">
            <span class="status-dot" :class="rec.status.toLowerCase()"></span>
            <span class="status-text" :class="rec.status.toLowerCase()">
              {{ 
                rec.status === 'COMPLETED' ? 'На сервере' :
                rec.status === 'MOVED_TO_LOCAL' ? 'Перенесен на ПК' :
                rec.status === 'DELETED' ? 'Удалён' :
                rec.status === 'FAILED' ? 'Сбой' : 'Запись'
              }}
            </span>
          </div>

          <div style="display: flex; gap: 0.5rem;">
            <a 
              v-if="rec.status === 'COMPLETED'"
              :href="'/api/recordings/' + rec.id + '/download'"
              class="btn-icon primary" 
              title="Скачать на ПК"
              download
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z"/>
              </svg>
            </a>
            <button 
              class="btn-icon danger" 
              title="Удалить запись"
              @click="handleDeleteRecording(rec.id)"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.archive-filters {
  display: flex;
  gap: 1rem;
  align-items: center;
}

@media (max-width: 600px) {
  .archive-filters {
    flex-direction: column;
    align-items: stretch;
  }
  .archive-filters input, .archive-filters > div {
    max-width: none !important;
    width: 100%;
  }
}
</style>
