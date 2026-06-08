<script setup lang="ts">
import { computed } from 'vue'
import { useTwitchStore } from '../store/twitchStore'

const store = useTwitchStore()

// circular progress math
const strokeDashoffset = computed(() => {
  const radius = 50
  const circumference = 2 * Math.PI * radius
  const freePercent = store.stats.freePercentage
  const usedPercent = 100 - freePercent
  return circumference - (usedPercent / 100) * circumference
})

// warning colors based on free space percentage
const activeRingClass = computed(() => {
  const freePercent = store.stats.freePercentage
  if (freePercent < 15) return 'danger'
  if (freePercent < 30) return 'warning'
  return ''
})

// Helper: Format file size
const formatSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<template>
  <section class="glass-panel disk-stats-widget">
    <div class="section-header" style="width: 100%; border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem;">
      <h2 style="font-size:1.1rem">Накопитель сервера</h2>
    </div>
    
    <div class="progress-circle">
      <svg viewBox="0 0 120 120">
        <circle class="bg-ring" cx="60" cy="60" r="50" />
        <circle 
          class="active-ring" 
          :class="activeRingClass"
          cx="60" 
          cy="60" 
          r="50" 
          stroke-dasharray="314.16" 
          :stroke-dashoffset="strokeDashoffset" 
        />
      </svg>
      <div class="progress-value">
        <span class="percent">{{ (100 - store.stats.freePercentage).toFixed(0) }}%</span>
        <span class="label">Занято</span>
      </div>
    </div>
    
    <div class="disk-text-info">
      <div>
        <span class="num">{{ formatSize(store.stats.used) }}</span>
        <span class="lbl">Занято</span>
      </div>
      <div>
        <span class="num">{{ formatSize(store.stats.free) }}</span>
        <span class="lbl">Свободно</span>
      </div>
      <div>
        <span class="num">{{ formatSize(store.stats.total) }}</span>
        <span class="lbl">Всего</span>
      </div>
    </div>

    <div style="font-size:0.8rem; color:var(--text-muted); text-align:left; width:100%; line-height:1.4; margin-top:0.5rem">
      📌 Настройте <strong>rclone</strong> на вашем ПК, чтобы он переносил файлы по SFTP. После скачивания файлы будут удаляться с сервера, освобождая это пространство.
    </div>
  </section>
</template>
