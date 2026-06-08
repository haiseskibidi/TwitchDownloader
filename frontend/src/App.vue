<script setup lang="ts">
import { onMounted } from 'vue'
import { useTwitchStore } from './store/twitchStore'
import DashboardView from './views/DashboardView.vue'
import ArchiveView from './views/ArchiveView.vue'
import SettingsView from './views/SettingsView.vue'
import TerminalDialog from './components/TerminalDialog.vue'

const store = useTwitchStore()

onMounted(async () => {
  // Initial data load
  await store.fetchStreamers()
  await store.fetchRecordings()
  await store.fetchStats()
  await store.fetchSettings()

  // Connect Websocket
  store.connectWebSocket()

  // Poll stats, recordings, and streamers every 30 seconds as a fallback
  setInterval(() => {
    store.fetchStats()
    store.fetchRecordings()
    store.fetchStreamers()
  }, 30000)
})
</script>

<template>
  <div class="app-container">
    <!-- Header -->
    <header class="app-header">
      <div class="logo">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24">
            <path d="M11.571 4.714h1.715v5.143H11.57zm4.715 0H18v5.143h-1.714zM6 0L1.714 4.286v15.428h5.143V24l4.286-4.286h3.428L22.286 12V0zm14.571 11.143l-3.428 3.428h-3.429l-3 3v-3H6.857V1.714h13.714Z"/>
          </svg>
        </div>
        <div class="logo-text">
          <h1>Twitch Auto-Recorder</h1>
          <span>Stream Capture & Sync</span>
        </div>
      </div>

      <!-- Tabs Navigation -->
      <nav class="nav-tabs">
        <button 
          class="tab-btn" 
          :class="{ active: store.activeTab === 'dashboard' }" 
          @click="store.activeTab = 'dashboard'"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"/>
          </svg>
          Дашборд
        </button>
        <button 
          class="tab-btn" 
          :class="{ active: store.activeTab === 'archive' }" 
          @click="store.activeTab = 'archive'"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12z"/>
          </svg>
          Архив записей
        </button>
        <button 
          class="tab-btn" 
          :class="{ active: store.activeTab === 'settings' }" 
          @click="store.activeTab = 'settings'"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
          </svg>
          Настройки
        </button>
      </nav>
    </header>

    <!-- Views switching -->
    <DashboardView v-if="store.activeTab === 'dashboard'" />
    <ArchiveView v-else-if="store.activeTab === 'archive'" />
    <SettingsView v-else-if="store.activeTab === 'settings'" />

    <!-- Terminal logs dialog -->
    <TerminalDialog />
  </div>
</template>

<style scoped>
/* App-specific transitions or adjustments */
</style>
