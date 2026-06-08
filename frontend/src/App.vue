<script setup lang="ts">
import { onMounted, watch, onUnmounted } from 'vue'
import { useTwitchStore } from './store/twitchStore'
import DashboardView from './views/DashboardView.vue'
import ArchiveView from './views/ArchiveView.vue'
import SettingsView from './views/SettingsView.vue'
import AuthView from './views/AuthView.vue'
import TerminalDialog from './components/TerminalDialog.vue'

const store = useTwitchStore()
let pollInterval: any = null

const startDataFetching = async () => {
  try {
    // Initial data load
    await Promise.all([
      store.fetchStreamers(),
      store.fetchRecordings(),
      store.fetchStats(),
      store.fetchSettings()
    ])
  } catch (err) {
    console.error('Error fetching dashboard data', err)
  }

  // Connect Websocket
  store.connectWebSocket()

  // Clear existing interval just in case
  if (pollInterval) {
    clearInterval(pollInterval)
  }

  // Poll stats, recordings, and streamers every 30 seconds as a fallback
  pollInterval = setInterval(() => {
    if (store.isAuthenticated) {
      store.fetchStats()
      store.fetchRecordings()
      store.fetchStreamers()
    }
  }, 30000)
}

const stopDataFetching = () => {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

onMounted(async () => {
  // First, verify current authentication session
  await store.checkAuth()
  
  if (store.isAuthenticated) {
    await startDataFetching()
  }
})

// Watch isAuthenticated to start or stop data loading and polling
watch(() => store.isAuthenticated, async (newVal) => {
  if (newVal) {
    await startDataFetching()
  } else {
    stopDataFetching()
  }
})

onUnmounted(() => {
  stopDataFetching()
})
</script>

<template>
  <!-- 1. Checking Auth full screen loader -->
  <div v-if="store.checkingAuth" class="auth-loading-screen" aria-live="polite" aria-busy="true">
    <div class="loader-logo pulse-animation">
      <svg viewBox="0 0 24 24">
        <path d="M11.571 4.714h1.715v5.143H11.57zm4.715 0H18v5.143h-1.714zM6 0L1.714 4.286v15.428h5.143V24l4.286-4.286h3.428L22.286 12V0zm14.571 11.143l-3.428 3.428h-3.429l-3 3v-3H6.857V1.714h13.714Z"/>
      </svg>
    </div>
    <div class="loader-bar">
      <div class="loader-progress"></div>
    </div>
  </div>

  <!-- 2. Authentication View when not logged in -->
  <AuthView v-else-if="!store.isAuthenticated" />

  <!-- 3. Authenticated App Layout -->
  <div v-else class="app-container">
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

      <div class="header-right">
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

        <!-- User badge & Logout -->
        <div class="user-profile">
          <span class="username-badge">{{ store.user?.username }}</span>
          <button class="logout-btn" @click="store.logout()" aria-label="Выйти" title="Выйти из системы">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59zM19 3H5c-1.11 0-2 .9-2 2v4h2V5h14v14H5v-4H3v4c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/>
            </svg>
          </button>
        </div>
      </div>
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
.header-right {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

@media (max-width: 768px) {
  .app-header {
    flex-direction: column;
    gap: 1.5rem;
    align-items: flex-start;
  }
  .header-right {
    width: 100%;
    justify-content: space-between;
  }
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: rgba(255, 255, 255, 0.02);
  padding: 0.3rem 0.3rem 0.3rem 0.85rem;
  border-radius: 10px;
  border: 1px solid var(--border-color);
}

.username-badge {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-helper);
}

.logout-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  width: 32px;
  height: 32px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.logout-btn:hover {
  color: var(--accent-red);
  background: var(--accent-red-muted);
}

/* Auth checking full-screen loader */
.auth-loading-screen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: var(--bg-main);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
  z-index: 9999;
}

.loader-logo {
  background: linear-gradient(135deg, var(--accent-lavender), #6366f1);
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10px 30px rgba(139, 92, 246, 0.3);
}

.loader-logo svg {
  width: 34px;
  height: 34px;
  fill: #fff;
}

.pulse-animation {
  animation: pulse-icon 1.8s infinite ease-in-out;
}

@keyframes pulse-icon {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.08); opacity: 0.8; }
}

.loader-bar {
  width: 140px;
  height: 3px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 3px;
  overflow: hidden;
  position: relative;
}

.loader-progress {
  position: absolute;
  height: 100%;
  width: 50%;
  background: linear-gradient(90deg, transparent, var(--accent-lavender), transparent);
  animation: progress-slide 1.5s infinite linear;
}

@keyframes progress-slide {
  0% { left: -50%; }
  100% { left: 100%; }
}
</style>
