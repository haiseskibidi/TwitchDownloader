<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps<{
  src: string
  title: string
  streamerName: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const videoRef = ref<HTMLVideoElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)

const isPlaying = ref(false)
const isMuted = ref(false)
const volume = ref(1.0)
const currentTime = ref(0)
const duration = ref(0)
const isFullscreen = ref(false)
const showControls = ref(true)
const isDraggingProgress = ref(false)
const hoverTimeText = ref('')
const hoverPercent = ref(0)
const showHoverProgress = ref(false)

const hasError = ref(false)
const errorDetails = ref('')

let controlsTimeout: any = null

const onVideoError = () => {
  hasError.value = true
  const error = videoRef.value?.error
  if (error) {
    if (error.code === 1) errorDetails.value = 'Загрузка видео прервана пользователем или системой.'
    else if (error.code === 2) errorDetails.value = 'Ошибка сети: не удалось загрузить поток видео с сервера.'
    else if (error.code === 3) errorDetails.value = 'Ошибка декодирования: видеофайл поврежден или кодек не поддерживается вашим браузером.'
    else if (error.code === 4) errorDetails.value = 'Видеофайл не найден на сервере (возможно, он был удален или перенесен rclone).'
    else errorDetails.value = error.message || 'Произошла ошибка при загрузке видео.'
  } else {
    errorDetails.value = 'Не удалось получить доступ к видеопотоку. Проверьте статус бэкенда.'
  }
}

const retryLoad = () => {
  if (!videoRef.value) return
  hasError.value = false
  errorDetails.value = ''
  videoRef.value.load()
  videoRef.value.play().catch(err => console.error('Play retry error', err))
}

const formatTime = (seconds: number) => {
  if (isNaN(seconds) || !isFinite(seconds)) return '00:00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

const togglePlay = () => {
  if (!videoRef.value) return
  if (videoRef.value.paused) {
    videoRef.value.play().catch(err => console.error('Play error', err))
    isPlaying.value = true
  } else {
    videoRef.value.pause()
    isPlaying.value = false
  }
  resetControlsTimeout()
}

const toggleMute = () => {
  if (!videoRef.value) return
  isMuted.value = !isMuted.value
  videoRef.value.muted = isMuted.value
  if (!isMuted.value && volume.value === 0) {
    volume.value = 0.5
    videoRef.value.volume = 0.5
  }
}

const onVolumeChange = (e: Event) => {
  if (!videoRef.value) return
  const val = parseFloat((e.target as HTMLInputElement).value)
  volume.value = val
  videoRef.value.volume = val
  isMuted.value = val === 0
  videoRef.value.muted = isMuted.value
}

const onTimeUpdate = () => {
  if (!videoRef.value || isDraggingProgress.value) return
  currentTime.value = videoRef.value.currentTime
}

const onLoadedMetadata = () => {
  if (!videoRef.value) return
  duration.value = videoRef.value.duration
}

const seek = (e: MouseEvent) => {
  if (!videoRef.value || !duration.value) return
  const rect = (e.currentTarget as HTMLDivElement).getBoundingClientRect()
  const clickX = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, clickX / rect.width))
  videoRef.value.currentTime = percent * duration.value
  currentTime.value = percent * duration.value
}

const handleProgressHover = (e: MouseEvent) => {
  if (!duration.value) return
  const rect = (e.currentTarget as HTMLDivElement).getBoundingClientRect()
  const hoverX = e.clientX - rect.left
  const percent = Math.max(0, Math.min(1, hoverX / rect.width))
  hoverPercent.value = percent
  hoverTimeText.value = formatTime(percent * duration.value)
  showHoverProgress.value = true
}

const hideProgressHover = () => {
  showHoverProgress.value = false
}

const toggleFullscreen = () => {
  if (!containerRef.value) return
  if (!document.fullscreenElement) {
    containerRef.value.requestFullscreen().catch(err => {
      console.error('Fullscreen request failed', err)
    })
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

const resetControlsTimeout = () => {
  showControls.value = true
  if (controlsTimeout) clearTimeout(controlsTimeout)
  if (isPlaying.value) {
    controlsTimeout = setTimeout(() => {
      showControls.value = false
    }, 2500)
  }
}

const onMouseMove = () => {
  resetControlsTimeout()
}

const onKeyDown = (e: KeyboardEvent) => {
  if (!videoRef.value) return
  const target = e.target as HTMLElement
  if (target.tagName === 'INPUT' && target.getAttribute('type') === 'text') return

  if (e.code === 'Space') {
    e.preventDefault()
    togglePlay()
  } else if (e.code === 'ArrowRight') {
    e.preventDefault()
    videoRef.value.currentTime = Math.min(duration.value, videoRef.value.currentTime + 5)
    currentTime.value = videoRef.value.currentTime
    resetControlsTimeout()
  } else if (e.code === 'ArrowLeft') {
    e.preventDefault()
    videoRef.value.currentTime = Math.max(0, videoRef.value.currentTime - 5)
    currentTime.value = videoRef.value.currentTime
    resetControlsTimeout()
  } else if (e.code === 'ArrowUp') {
    e.preventDefault()
    const newVol = Math.min(1.0, videoRef.value.volume + 0.1)
    volume.value = newVol
    videoRef.value.volume = newVol
    isMuted.value = false
    videoRef.value.muted = false
    resetControlsTimeout()
  } else if (e.code === 'ArrowDown') {
    e.preventDefault()
    const newVol = Math.max(0.0, videoRef.value.volume - 0.1)
    volume.value = newVol
    videoRef.value.volume = newVol
    isMuted.value = newVol === 0
    videoRef.value.muted = isMuted.value
    resetControlsTimeout()
  } else if (e.code === 'KeyF') {
    e.preventDefault()
    toggleFullscreen()
  } else if (e.code === 'Escape') {
    if (!isFullscreen.value) {
      emit('close')
    }
  }
}

onMounted(() => {
  resetControlsTimeout()
  document.addEventListener('keydown', onKeyDown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onUnmounted(() => {
  if (controlsTimeout) clearTimeout(controlsTimeout)
  document.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})

watch(() => props.src, () => {
  isPlaying.value = false
  currentTime.value = 0
  duration.value = 0
  hasError.value = false
  errorDetails.value = ''
  resetControlsTimeout()
})
</script>

<template>
  <div class="player-overlay" @click.self="emit('close')">
    <div 
      ref="containerRef" 
      class="player-container" 
      :class="{ 'hide-cursor': !showControls && isPlaying }"
      @mousemove="onMouseMove"
      @mouseleave="showControls = false"
    >
      <!-- Video Element -->
      <video
        ref="videoRef"
        :src="src"
        class="video-element"
        @click="togglePlay"
        @dblclick="toggleFullscreen"
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onLoadedMetadata"
        @play="isPlaying = true"
        @pause="isPlaying = false"
        @error="onVideoError"
      ></video>

      <!-- Error Overlay (Beautiful alert when video fails to load) -->
      <div v-if="hasError" class="player-error-overlay">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="currentColor" class="error-icon">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
        </svg>
        <p class="error-message">{{ errorDetails }}</p>
        <button class="btn-retry" @click="retryLoad">Попробовать снова</button>
      </div>

      <!-- Glassmorphic Header (Title + Close Button) -->
      <Transition name="fade">
        <div v-show="showControls" class="player-header">
          <div class="header-info">
            <span class="streamer-badge">@{{ streamerName }}</span>
            <h3 class="stream-title" :title="title">{{ title }}</h3>
          </div>
          <button class="close-btn" @click="emit('close')" title="Закрыть (Esc)">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
              <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
            </svg>
          </button>
        </div>
      </Transition>

      <!-- Center Big Play Button (Kinescope style scale effect) -->
      <div class="center-play-wrapper" @click="togglePlay">
        <Transition name="scale-fade">
          <div v-if="!isPlaying" class="center-play-btn">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="currentColor" style="margin-left: 4px;">
              <path d="M8 5v14l11-7z"/>
            </svg>
          </div>
        </Transition>
      </div>

      <!-- Glassmorphic Controls Bar -->
      <Transition name="slide-up">
        <div v-show="showControls" class="player-controls-panel">
          <!-- Custom Progress Bar -->
          <div 
            class="progress-container"
            @click="seek"
            @mousemove="handleProgressHover"
            @mouseleave="hideProgressHover"
          >
            <div class="progress-bar-bg"></div>
            <!-- Hover Progress Highlight -->
            <div 
              v-show="showHoverProgress" 
              class="progress-hover-line" 
              :style="{ width: (hoverPercent * 100) + '%' }"
            ></div>
            <!-- Current Played Progress -->
            <div 
              class="progress-played" 
              :style="{ width: (duration ? (currentTime / duration * 100) : 0) + '%' }"
            >
              <div class="progress-handler"></div>
            </div>
            <!-- Time tooltip on hover -->
            <div 
              v-show="showHoverProgress" 
              class="hover-time-tooltip"
              :style="{ left: `calc(${hoverPercent * 100}% - 30px)` }"
            >
              {{ hoverTimeText }}
            </div>
          </div>

          <!-- Bottom Bar Controls -->
          <div class="controls-row">
            <!-- Left Group -->
            <div class="controls-group">
              <button class="control-btn" @click="togglePlay" :title="isPlaying ? 'Пауза (Space)' : 'Воспроизведение (Space)'">
                <svg v-if="isPlaying" viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                  <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                  <path d="M8 5v14l11-7z"/>
                </svg>
              </button>

              <!-- Volume group -->
              <div class="volume-control-group">
                <button class="control-btn" @click="toggleMute" title="Звук">
                  <svg v-if="isMuted || volume === 0" viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                    <path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77zM4.34 2.93L2.93 4.34 7.29 8.7H3v6.6h4.4l5.6 5.6v-8.29l4.73 4.73c-.76.53-1.6.93-2.51 1.17v2.04c1.46-.32 2.8-.99 3.91-1.92l2.45 2.45 1.41-1.41L4.34 2.93zM10 15.17l-2.78-2.78H5v-2.2h2.22l.53-.53L10 11.88v3.29z"/>
                  </svg>
                  <svg v-else-if="volume < 0.5" viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                    <path d="M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z"/>
                  </svg>
                  <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                    <path d="M3 9v6h4l5 5V4L9 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
                  </svg>
                </button>
                <input 
                  type="range" 
                  min="0" 
                  max="1" 
                  step="0.05" 
                  :value="isMuted ? 0 : volume" 
                  @input="onVolumeChange"
                  class="volume-slider" 
                  title="Громкость"
                />
              </div>

              <!-- Time display -->
              <span class="time-display">
                {{ formatTime(currentTime) }} <span class="time-divider">/</span> {{ formatTime(duration) }}
              </span>
            </div>

            <!-- Right Group -->
            <div class="controls-group">
              <button class="control-btn" @click="toggleFullscreen" :title="isFullscreen ? 'Оконный режим (F)' : 'На весь экран (F)'">
                <svg v-if="isFullscreen" viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                  <path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
                  <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.player-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(8px);
  z-index: 9999;
  display: flex;
  justify-content: center;
  align-items: center;
}

.player-container {
  position: relative;
  width: 90%;
  max-width: 1200px;
  aspect-ratio: 16/9;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.1);
  display: flex;
  justify-content: center;
  align-items: center;
}

.hide-cursor {
  cursor: none;
}

.video-element {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

/* Header Area styling */
.player-header {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: 1.25rem 1.5rem;
  background: linear-gradient(to bottom, rgba(0,0,0,0.8) 0%, rgba(0,0,0,0) 100%);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  z-index: 10;
  pointer-events: auto;
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-width: 80%;
}

.streamer-badge {
  font-size: 0.75rem;
  font-weight: 700;
  background: linear-gradient(135deg, #a855f7 0%, #6366f1 100%);
  color: #fff;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  width: max-content;
}

.stream-title {
  font-size: 1.15rem;
  font-weight: 600;
  color: #fff;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.8);
}

.close-btn {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(4px);
  border: none;
  color: #fff;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: rgba(239, 68, 68, 0.8);
  transform: scale(1.05);
}

/* Center Play Button Styling */
.center-play-wrapper {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 5;
  cursor: pointer;
}

.center-play-btn {
  width: 76px;
  height: 76px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  color: #fff;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.center-play-wrapper:hover .center-play-btn {
  background: rgba(255, 255, 255, 0.25);
  transform: scale(1.1);
  box-shadow: 0 12px 40px rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.4);
}

/* Controls Panel Styling */
.player-controls-panel {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 1.5rem;
  background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0) 100%);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  z-index: 10;
}

/* Progress bar style */
.progress-container {
  position: relative;
  height: 6px;
  width: 100%;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: height 0.15s ease;
}

.progress-container:hover {
  height: 10px;
}

.progress-bar-bg {
  position: absolute;
  left: 0;
  right: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

.progress-hover-line {
  position: absolute;
  left: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
  pointer-events: none;
}

.progress-played {
  position: absolute;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, #6366f1 0%, #a855f7 100%);
  border-radius: 3px;
  pointer-events: none;
  position: relative;
}

.progress-handler {
  position: absolute;
  right: -6px;
  top: 50%;
  transform: translateY(-50%) scale(0);
  width: 12px;
  height: 12px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  transition: transform 0.15s ease;
}

.progress-container:hover .progress-handler {
  transform: translateY(-50%) scale(1);
}

.hover-time-tooltip {
  position: absolute;
  bottom: 20px;
  background: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(4px);
  color: #fff;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  pointer-events: none;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

/* Control buttons row */
.controls-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.controls-group {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.control-btn {
  background: none;
  border: none;
  color: #e5e7eb;
  padding: 0;
  cursor: pointer;
  display: flex;
  justify-content: center;
  align-items: center;
  transition: color 0.2s ease, transform 0.1s ease;
}

.control-btn:hover {
  color: #a855f7;
  transform: scale(1.05);
}

.control-btn:active {
  transform: scale(0.95);
}

/* Volume controls */
.volume-control-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.volume-slider {
  -webkit-appearance: none;
  appearance: none;
  width: 0;
  height: 4px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 2px;
  outline: none;
  opacity: 0;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s ease;
}

.volume-control-group:hover .volume-slider,
.volume-slider:focus,
.volume-slider:active {
  width: 70px;
  opacity: 1;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.volume-slider::-webkit-slider-thumb:hover {
  transform: scale(1.25);
  background: #a855f7;
}

.time-display {
  font-size: 0.85rem;
  color: #9ca3af;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.time-divider {
  color: #4b5563;
  margin: 0 0.15rem;
}

/* Transitions animation */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(20px);
  opacity: 0;
}

.scale-fade-enter-active,
.scale-fade-leave-active {
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.2s ease;
}

.scale-fade-enter-from,
.scale-fade-leave-to {
  transform: scale(0.5);
  opacity: 0;
}

/* Error Overlay Styles */
.player-error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 15, 20, 0.9);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 2rem;
  text-align: center;
  z-index: 8;
  color: #fca5a5;
}

.error-icon {
  color: #ef4444;
  margin-bottom: 1.25rem;
  filter: drop-shadow(0 0 10px rgba(239, 68, 68, 0.4));
  animation: bounce 2s infinite;
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}

.error-message {
  font-size: 1rem;
  font-weight: 500;
  max-width: 500px;
  line-height: 1.5;
  color: #e5e7eb;
  margin: 0 0 1.5rem 0;
}

.btn-retry {
  background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%);
  border: none;
  color: #fff;
  padding: 0.65rem 1.5rem;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.25);
}

.btn-retry:hover {
  transform: translateY(-1px);
  filter: brightness(1.1);
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
}

.btn-retry:active {
  transform: translateY(0);
}
</style>
