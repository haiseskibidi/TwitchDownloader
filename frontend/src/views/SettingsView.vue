<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTwitchStore } from '../store/twitchStore'
import CustomSelect from '../components/CustomSelect.vue'

const store = useTwitchStore()

const settingsForm = ref({
  twitch_client_id: '',
  twitch_client_secret: '',
  download_path: 'downloads',
  download_quality: 'best',
  disk_cleanup_enabled: 'true',
  free_space_threshold_percent: '10'
})

const isSavingSettings = ref(false)
const saveSuccess = ref(false)

const qualityOptions = [
  { value: 'best', label: 'Лучшее (Source)' },
  { value: '1080p', label: '1080p' },
  { value: '720p', label: '720p' },
  { value: '480p', label: '480p' },
  { value: 'worst', label: 'Худшее' }
]

onMounted(async () => {
  await store.fetchSettings()
  settingsForm.value = { ...store.settings }
})

// Save settings handler
const handleSaveSettings = async () => {
  isSavingSettings.value = true
  saveSuccess.value = false
  
  const ok = await store.saveSettings(settingsForm.value)
  isSavingSettings.value = false
  
  if (ok) {
    saveSuccess.value = true
    setTimeout(() => {
      saveSuccess.value = false
    }, 3000)
  }
}
</script>

<template>
  <main style="max-width: 600px; margin: 0 auto;">
    <!-- Storage and cleanup parameters -->
    <section class="glass-panel" style="display:flex; flex-direction:column; justify-content:space-between; overflow: visible;">
      <div>
        <div class="section-header" style="border-bottom: 1px solid var(--border-color); padding-bottom: 0.5rem; margin-bottom: 1.25rem;">
          <h2>Параметры хранилища</h2>
        </div>
        
        <div class="form-group" style="position: relative;">
          <label>Качество видео по умолчанию</label>
          <CustomSelect 
            v-model="settingsForm.download_quality" 
            :options="qualityOptions" 
            placeholder="Качество"
          />
        </div>

        <div class="form-group">
          <label>Папка загрузок на сервере</label>
          <input 
            type="text" 
            class="input-text" 
            v-model="settingsForm.download_path" 
            placeholder="downloads"
          />
        </div>

        <div class="form-group" style="margin-top:1.5rem">
          <label class="switch-label">
            <div class="switch">
              <input 
                type="checkbox" 
                v-model="settingsForm.disk_cleanup_enabled" 
                true-value="true"
                false-value="false"
              />
              <span class="slider"></span>
            </div>
            <span style="font-weight:600; color:var(--text-main)">Автоочистка диска</span>
          </label>
          <span style="font-size:0.8rem; color:var(--text-muted); margin-left: 3rem">
            Удалять старые файлы стримов на сервере при заполнении диска
          </span>
        </div>

        <div class="form-group" v-if="settingsForm.disk_cleanup_enabled === 'true'" style="margin-left: 3rem; max-width: 200px;">
          <label>Порог очистки (свободно %)</label>
          <input 
            type="number" 
            class="input-text" 
            v-model="settingsForm.free_space_threshold_percent" 
            min="5" 
            max="50"
          />
        </div>
      </div>

      <div style="margin-top: 2rem; border-top: 1px solid var(--border-color); padding-top: 1.25rem; display: flex; align-items: center; gap: 1rem;">
        <button class="btn btn-primary" @click="handleSaveSettings" :disabled="isSavingSettings">
          <span>{{ isSavingSettings ? 'Сохранение...' : 'Сохранить настройки' }}</span>
        </button>
        <span v-if="saveSuccess" style="color:var(--accent-green); font-size:0.9rem; font-weight: 500">
          Настройки сохранены!
        </span>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* Settings specific styles if any */
</style>
