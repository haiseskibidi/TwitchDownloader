<script setup lang="ts">
const props = defineProps<{
  streamer: {
    id: number
    twitchUsername: string
    displayName: string
    twitchId: string
    profileImageUrl?: string
    isActive: boolean
    addedAt: string
    isRecording: boolean
  }
}>()

const emit = defineEmits(['toggle-active', 'delete', 'view-logs'])

const handleToggle = () => {
  emit('toggle-active', props.streamer.id, !props.streamer.isActive)
}

const handleDelete = () => {
  emit('delete', props.streamer.id)
}

const handleViewLogs = () => {
  emit('view-logs', props.streamer.twitchUsername)
}
</script>

<template>
  <div 
    class="streamer-card"
    :class="{ 
      recording: streamer.isRecording
    }"
  >
    <div class="streamer-card-header">
      <div style="display: flex; gap: 0.75rem; align-items: center; max-width: 70%;">
        <!-- Streamer Avatar -->
        <div class="avatar-container">
          <img 
            :src="streamer.profileImageUrl || 'https://static-cdn.jtvnw.net/user-default-pictures-uv/cdd517ad-def4-437e-92f0-787cd6952afb-profile_image-300x300.png'" 
            class="streamer-avatar" 
            alt="Avatar"
          />
        </div>
        
        <div class="streamer-info">
          <a :href="'https://twitch.tv/' + streamer.twitchUsername" target="_blank" rel="noopener noreferrer" class="streamer-link" title="Открыть на Twitch">
            <span class="streamer-name">{{ streamer.displayName }}</span>
            <span class="streamer-username">twitch.tv/{{ streamer.twitchUsername }}</span>
          </a>
        </div>
      </div>

      <span 
        class="status-badge"
        :class="{ 
          recording: streamer.isRecording, 
          offline: !streamer.isRecording 
        }"
      >
        {{ streamer.isRecording ? 'Запись' : 'Офлайн' }}
      </span>
    </div>
    
    <div class="streamer-actions">
      <label class="switch-label">
        <div class="switch">
          <input 
            type="checkbox" 
            :checked="streamer.isActive" 
            @change="handleToggle"
          />
          <span class="slider"></span>
        </div>
        <span>{{ streamer.isActive ? 'Следить' : 'Пауза' }}</span>
      </label>

      <div style="display: flex; gap: 0.25rem;">
        <button 
          v-if="streamer.isRecording"
          class="btn-icon primary" 
          title="Показать консоль логов"
          @click="handleViewLogs"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
            <path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm-8 12H4v-2h8v2zm8-4H4V9.9h16V12zm0-4H4V6h16v2z"/>
          </svg>
        </button>
        <button 
          class="btn-icon danger" 
          title="Удалить"
          @click="handleDelete"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>
