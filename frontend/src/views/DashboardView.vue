<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useTwitchStore, type TwitchSearchChannel } from '../store/twitchStore'
import StreamerCard from '../components/StreamerCard.vue'
import ActiveDownloads from '../components/ActiveDownloads.vue'
import DiskGauge from '../components/DiskGauge.vue'

const store = useTwitchStore()

// Local states for forms
const newStreamerName = ref('')
const isAddingStreamer = ref(false)
const addError = ref('')
const showDropdown = ref(false)
const selectedChannelObj = ref<TwitchSearchChannel | null>(null)
const searchContainer = ref<HTMLElement | null>(null)

let debounceTimer: any = null

const onInput = () => {
  showDropdown.value = true
  selectedChannelObj.value = null // Reset selection if they type
  
  if (debounceTimer) clearTimeout(debounceTimer)
  
  debounceTimer = setTimeout(() => {
    store.searchChannels(newStreamerName.value)
  }, 300)
}

const selectChannel = (channel: TwitchSearchChannel) => {
  newStreamerName.value = channel.broadcaster_login
  selectedChannelObj.value = channel
  showDropdown.value = false
}

// Add Streamer handler
const handleAddStreamer = async () => {
  const username = newStreamerName.value.trim()
  if (!username) return
  
  isAddingStreamer.value = true
  addError.value = ''
  
  let result
  if (selectedChannelObj.value && selectedChannelObj.value.broadcaster_login.toLowerCase() === username.toLowerCase()) {
    result = await store.addStreamer(username, {
      displayName: selectedChannelObj.value.display_name,
      twitchId: selectedChannelObj.value.id,
      profileImageUrl: selectedChannelObj.value.thumbnail_url
    })
  } else {
    result = await store.addStreamer(username)
  }
  
  isAddingStreamer.value = false
  
  if (result.success) {
    newStreamerName.value = ''
    selectedChannelObj.value = null
    store.searchChannelResults = []
  } else {
    addError.value = result.error || 'Failed to add streamer'
  }
}

// Toggle active status handler
const handleToggleStreamer = async (id: number, isActive: boolean) => {
  await store.toggleStreamer(id, isActive)
}

// Delete streamer handler
const handleDeleteStreamer = async (id: number) => {
  if (confirm('Are you sure you want to remove this streamer? This will stop active recordings.')) {
    await store.deleteStreamer(id)
  }
}

// View logs dialog trigger
const handleViewLogs = (username: string) => {
  store.selectStreamerForLogs(username)
}

const handleClickOutside = (event: MouseEvent) => {
  if (searchContainer.value && !searchContainer.value.contains(event.target as Node)) {
    showDropdown.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="dashboard-layout">
    <!-- Left side: Streamers and Active downloads -->
    <div class="dashboard-main-section">
      <!-- Add & Track Streamers -->
      <section class="glass-panel" style="margin-bottom: 2rem; overflow: visible;">
        <div class="section-header" style="margin-bottom: 1rem;">
          <h2>Добавить стримера для записи</h2>
        </div>
        <form @submit.prevent="handleAddStreamer" class="form-inline" style="position: relative;">
          <div class="search-autocomplete-container" ref="searchContainer" style="flex: 1; position: relative;">
            <input 
              type="text" 
              class="input-text" 
              v-model="newStreamerName" 
              @input="onInput"
              @focus="showDropdown = true"
              placeholder="Twitch логин (например: ninja)" 
              :disabled="isAddingStreamer"
              style="width: 100%;"
            />
            <!-- Autocomplete list -->
            <transition name="fade-slide">
              <div v-if="showDropdown && store.searchChannelResults.length > 0" class="autocomplete-dropdown">
                <div 
                  v-for="ch in store.searchChannelResults" 
                  :key="ch.id" 
                  class="autocomplete-item"
                  @click="selectChannel(ch)"
                >
                  <div class="autocomplete-avatar-container">
                    <img 
                      :src="ch.thumbnail_url || 'https://static-cdn.jtvnw.net/user-default-pictures-uv/cdd517ad-def4-437e-92f0-787cd6952afb-profile_image-300x300.png'" 
                      class="autocomplete-avatar" 
                      alt="Avatar"
                    />
                  </div>
                  <div class="autocomplete-info">
                    <span class="autocomplete-name">{{ ch.display_name }}</span>
                    <span class="autocomplete-login">twitch.tv/{{ ch.broadcaster_login }}</span>
                  </div>
                  <span v-if="ch.is_live" class="live-dot-badge">LIVE</span>
                </div>
              </div>
            </transition>
          </div>
          <button type="submit" class="btn btn-primary" :disabled="isAddingStreamer">
            <span v-if="isAddingStreamer">Поиск...</span>
            <span v-else>Добавить</span>
          </button>
        </form>
        <p v-if="addError" style="color: var(--accent-red); font-size: 0.85rem; margin-top: 0.5rem;">
          {{ addError }}
        </p>
      </section>

      <!-- Streamers grid -->
      <section>
        <div class="section-header">
          <h2>Отслеживаемые каналы</h2>
        </div>
        <div v-if="store.streamers.length === 0" class="empty-state">
          Список стримеров пуст. Введите имя пользователя выше, чтобы начать отслеживание.
        </div>
        <div v-else class="streamers-grid">
          <StreamerCard
            v-for="s in store.streamers"
            :key="s.id"
            :streamer="s"
            @toggle-active="handleToggleStreamer"
            @delete="handleDeleteStreamer"
            @view-logs="handleViewLogs"
          />
        </div>
      </section>

      <!-- Active downloads -->
      <ActiveDownloads />
    </div>

    <!-- Right side: Disk stats widget -->
    <aside class="dashboard-sidebar">
      <DiskGauge />
    </aside>
  </div>
</template>

<style scoped>
.search-autocomplete-container {
  position: relative;
}

.autocomplete-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  background: #111215;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  z-index: 1000;
  max-height: 250px;
  overflow-y: auto;
  box-shadow: var(--shadow-soft);
  padding: 0.25rem;
}

.autocomplete-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  font-size: 0.85rem;
  color: var(--text-helper);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.1s ease;
}

.autocomplete-item:hover {
  background: rgba(255, 255, 255, 0.04);
  color: #fff;
}

.autocomplete-avatar-container {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.autocomplete-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.autocomplete-avatar-placeholder {
  width: 100%;
  height: 100%;
  background: var(--accent-lavender-muted);
  color: var(--accent-lavender);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.85rem;
}

.autocomplete-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.autocomplete-name {
  font-weight: 500;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.autocomplete-login {
  font-size: 0.75rem;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.live-dot-badge {
  background: var(--accent-red-muted);
  color: var(--accent-red);
  font-size: 0.65rem;
  font-weight: 700;
  padding: 0.15rem 0.35rem;
  border-radius: 4px;
  letter-spacing: 0.5px;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

/* Transitions */
.fade-slide-enter-active, .fade-slide-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.fade-slide-enter-from, .fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
