<script setup lang="ts">
import { ref, computed } from 'vue'
import { useTwitchStore } from '../store/twitchStore'
import CustomSelect from '../components/CustomSelect.vue'

const store = useTwitchStore()

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
const getDuration = (startedAt: string, endedAt: string) => {
  if (!startedAt || !endedAt) return '-'
  const start = new Date(startedAt).getTime()
  const end = new Date(endedAt).getTime()
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
            {{ getDuration(rec.startedAt, rec.endedAt) }}
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

          <div>
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
