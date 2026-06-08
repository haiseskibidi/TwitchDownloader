<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useTwitchStore } from '../store/twitchStore'

const store = useTwitchStore()
const logDialog = ref<HTMLDialogElement | null>(null)
const terminalBody = ref<HTMLElement | null>(null)

// Watch streamer log selection to open/close native dialog
watch(() => store.selectedStreamerLogs, (newVal) => {
  if (newVal) {
    logDialog.value?.showModal()
    scrollToBottom()
  } else {
    logDialog.value?.close()
  }
})

// Scroll to bottom helper
const scrollToBottom = () => {
  nextTick(() => {
    if (terminalBody.value) {
      terminalBody.value.scrollTop = terminalBody.value.scrollHeight
    }
  })
}

// Watch log lines to scroll to bottom automatically
const currentStreamerLogs = computed(() => {
  const user = store.selectedStreamerLogs.toLowerCase()
  return store.activeLogs[user] || ['[SYSTEM] Waiting for streamlink output...']
})

watch(currentStreamerLogs, () => {
  scrollToBottom()
}, { deep: true })

const closeLogs = () => {
  store.selectStreamerForLogs('')
}

const handleDialogClose = () => {
  store.selectStreamerForLogs('')
}

// Fallback boundary click handler for Safari/older browsers without closedby="any" support
const handleDialogBackdropClick = (event: MouseEvent) => {
  const dialog = logDialog.value
  if (!dialog) return
  
  // If browser supports native closedby, let it handle it
  if ('closedBy' in HTMLDialogElement.prototype) return
  
  // Check if click was on backdrop (outside content area)
  if (event.target === dialog) {
    const rect = dialog.getBoundingClientRect()
    const isInside = (
      rect.top <= event.clientY &&
      event.clientY <= rect.top + rect.height &&
      rect.left <= event.clientX &&
      event.clientX <= rect.left + rect.width
    )
    if (!isInside) {
      dialog.close()
    }
  }
}
</script>

<template>
  <dialog 
    ref="logDialog" 
    class="terminal-dialog" 
    closedby="any"
    @close="handleDialogClose"
    @click="handleDialogBackdropClick"
  >
    <div class="terminal-container" @click.stop>
      <header class="terminal-header">
        <div class="terminal-title">
          <span class="terminal-dot"></span>
          console_log://twitch.tv/{{ store.selectedStreamerLogs }}
        </div>
        <button class="btn-icon" @click="closeLogs">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
          </svg>
        </button>
      </header>
      <div class="terminal-body" ref="terminalBody">
        <div 
          v-for="(line, index) in currentStreamerLogs" 
          :key="index" 
          class="terminal-line"
          :class="{ 
            error: line.includes('[ERROR]') || line.includes('error'),
            system: line.startsWith('[SYSTEM]') 
          }"
        >
          {{ line }}
        </div>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
/* Scoped adjustments if any */
</style>
