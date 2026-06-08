<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useTwitchStore } from '../store/twitchStore'

const store = useTwitchStore()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const isLoginMode = ref(true)
const isLoading = ref(false)
const errorMessage = ref('')
const errorShake = ref(false)

// Set mode depending on user presence
onMounted(async () => {
  await store.checkSystemUsers()
  if (!store.hasSystemUsers) {
    isLoginMode.value = false
  }
})

const setMode = (login: boolean) => {
  isLoginMode.value = login
  errorMessage.value = ''
  confirmPassword.value = ''
}

const clearErrors = () => {
  errorMessage.value = ''
}

const isFormValid = computed(() => {
  if (username.value.trim().length < 3) return false
  if (password.value.length < 6) return false
  if (!isLoginMode.value && password.value !== confirmPassword.value) return false
  return true
})

const submitText = computed(() => {
  if (isLoading.value) return ''
  if (!store.hasSystemUsers) return 'Создать аккаунт администратора'
  return isLoginMode.value ? 'Войти' : 'Зарегистрироваться'
})

const triggerShake = () => {
  errorShake.value = true
  setTimeout(() => {
    errorShake.value = false
  }, 400)
}

const handleSubmit = async () => {
  if (!isFormValid.value) return

  isLoading.value = true
  errorMessage.value = ''

  try {
    if (isLoginMode.value) {
      const result = await store.login(username.value, password.value)
      if (!result.success) {
        errorMessage.value = result.error || 'Неверное имя пользователя или пароль'
        triggerShake()
      }
    } else {
      if (password.value !== confirmPassword.value) {
        errorMessage.value = 'Пароли не совпадают'
        triggerShake()
        isLoading.value = false
        return
      }
      const result = await store.register(username.value, password.value)
      if (!result.success) {
        errorMessage.value = result.error || 'Ошибка при регистрации'
        triggerShake()
      }
    }
  } catch (err) {
    errorMessage.value = 'Произошла ошибка при соединении с сервером'
    triggerShake()
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="auth-wrapper">
    <div class="auth-card" :class="{ 'shake-anim': errorShake }">
      <!-- Logo & Header -->
      <div class="auth-header">
        <div class="logo-icon animate-pulse-logo">
          <svg viewBox="0 0 24 24">
            <path d="M11.571 4.714h1.715v5.143H11.57zm4.715 0H18v5.143h-1.714zM6 0L1.714 4.286v15.428h5.143V24l4.286-4.286h3.428L22.286 12V0zm14.571 11.143l-3.428 3.428h-3.429l-3 3v-3H6.857V1.714h13.714Z"/>
          </svg>
        </div>
        <h2>Twitch Auto-Recorder</h2>
        <p v-if="!store.hasSystemUsers" class="setup-subtitle">
          Первый запуск: создайте аккаунт администратора для защиты сервера
        </p>
        <p v-else class="setup-subtitle">
          Вход в систему управления записями
        </p>
      </div>

      <!-- Mode Selector (Tabs) -->
      <div v-if="store.hasSystemUsers" class="auth-tabs">
        <button 
          type="button" 
          class="auth-tab-btn" 
          :class="{ active: isLoginMode }" 
          @click="setMode(true)"
        >
          Вход
        </button>
        <button 
          type="button" 
          class="auth-tab-btn" 
          :class="{ active: !isLoginMode }" 
          @click="setMode(false)"
        >
          Регистрация
        </button>
      </div>

      <!-- Error alert -->
      <div v-if="errorMessage" class="error-alert" role="alert">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
        </svg>
        <span>{{ errorMessage }}</span>
      </div>

      <!-- Form -->
      <form @submit.prevent="handleSubmit" class="auth-form">
        <div class="form-group">
          <label for="username">Имя пользователя</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
            </svg>
            <input 
              id="username" 
              type="text" 
              v-model="username" 
              placeholder="Введите имя пользователя" 
              required 
              autocomplete="username"
              :disabled="isLoading"
              @input="clearErrors"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="password">Пароль</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>
            </svg>
            <input 
              id="password" 
              type="password" 
              v-model="password" 
              placeholder="Минимум 6 символов" 
              required 
              autocomplete="current-password"
              :disabled="isLoading"
              @input="clearErrors"
            />
          </div>
        </div>

        <div v-if="!isLoginMode" class="form-group">
          <label for="confirmPassword">Подтвердите пароль</label>
          <div class="input-wrapper">
            <svg class="input-icon" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>
            </svg>
            <input 
              id="confirmPassword" 
              type="password" 
              v-model="confirmPassword" 
              placeholder="Повторите введенный пароль" 
              required 
              autocomplete="new-password"
              :disabled="isLoading"
              @input="clearErrors"
            />
          </div>
        </div>

        <button type="submit" class="submit-btn" :disabled="isLoading || !isFormValid">
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>{{ submitText }}</span>
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.auth-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 80vh;
  padding: 1.5rem;
}

.auth-card {
  background: var(--glass-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-soft);
  border-radius: 16px;
  width: 100%;
  max-width: 420px;
  padding: 2.5rem;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.auth-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: 2rem;
}

.logo-icon {
  background: linear-gradient(135deg, var(--accent-lavender), #6366f1);
  width: 54px;
  height: 54px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1rem;
  box-shadow: 0 8px 20px rgba(139, 92, 246, 0.25);
}

.logo-icon svg {
  width: 28px;
  height: 28px;
  fill: #fff;
}

.animate-pulse-logo {
  animation: pulse-logo 3s infinite alternate;
}

@keyframes pulse-logo {
  0% { transform: scale(1); filter: brightness(1); }
  100% { transform: scale(1.05); filter: brightness(1.1); }
}

.auth-header h2 {
  font-family: 'Outfit', sans-serif;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 0.5rem;
}

.setup-subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  line-height: 1.4;
  max-width: 300px;
}

.auth-tabs {
  display: flex;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--border-color);
  padding: 0.25rem;
  border-radius: 10px;
  margin-bottom: 1.5rem;
}

.auth-tab-btn {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--text-muted);
  padding: 0.6rem;
  font-size: 0.875rem;
  font-weight: 500;
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.auth-tab-btn:hover {
  color: var(--text-main);
}

.auth-tab-btn.active {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.error-alert {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: var(--accent-red-muted);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #fca5a5;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  font-size: 0.85rem;
  margin-bottom: 1.5rem;
  line-height: 1.3;
}

.error-alert svg {
  flex-shrink: 0;
  color: var(--accent-red);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.form-group label {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--text-helper);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 1rem;
  color: var(--text-muted);
  pointer-events: none;
}

.input-wrapper input {
  width: 100%;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 0.75rem 1rem 0.75rem 2.5rem;
  color: #fff;
  font-family: inherit;
  font-size: 0.95rem;
  transition: all 0.2s ease;
}

.input-wrapper input:focus {
  outline: none;
  border-color: var(--accent-lavender);
  background: rgba(255, 255, 255, 0.04);
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.15);
}

.submit-btn {
  background: linear-gradient(135deg, var(--accent-lavender), #6366f1);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 0.85rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 0.5rem;
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.2);
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(139, 92, 246, 0.3);
  filter: brightness(1.05);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Shake Animation */
.shake-anim {
  animation: shake 0.3s ease-in-out;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25%, 75% { transform: translateX(-3px); }
  50% { transform: translateX(3px); }
}
</style>
