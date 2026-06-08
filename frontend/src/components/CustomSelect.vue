<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'

const props = defineProps<{
  modelValue: any
  options: { value: any; label: string }[]
  placeholder?: string
}>()

const emit = defineEmits(['update:modelValue'])

const isOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const selectedLabel = computed(() => {
  const selected = props.options.find(opt => opt.value === props.modelValue)
  return selected ? selected.label : props.placeholder || 'Select option'
})

const toggleDropdown = () => {
  isOpen.value = !isOpen.value
}

const selectOption = (value: any) => {
  emit('update:modelValue', value)
  isOpen.value = false
}

const handleClickOutside = (event: MouseEvent) => {
  if (containerRef.value && !containerRef.value.contains(event.target as Node)) {
    isOpen.value = false
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
  <div class="custom-select-container" ref="containerRef">
    <button type="button" class="custom-select-trigger" @click="toggleDropdown" :class="{ open: isOpen }">
      <span>{{ selectedLabel }}</span>
      <svg class="arrow-icon" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
        <path d="M7 10l5 5 5-5z"/>
      </svg>
    </button>
    <transition name="fade-slide">
      <div v-if="isOpen" class="custom-select-dropdown">
        <div 
          v-for="opt in options" 
          :key="opt.value" 
          class="custom-select-option"
          :class="{ selected: opt.value === modelValue }"
          @click="selectOption(opt.value)"
        >
          {{ opt.label }}
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.custom-select-container {
  position: relative;
  width: 100%;
}

.custom-select-trigger {
  background: rgba(0, 0, 0, 0.15);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: #fff;
  padding: 0.55rem 0.75rem;
  font-size: 0.85rem;
  font-weight: 500;
  outline: none;
  transition: all 0.15s ease;
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  text-align: left;
}

.custom-select-trigger:hover, .custom-select-trigger.open {
  border-color: var(--accent-lavender);
}

.arrow-icon {
  transition: transform 0.15s ease;
  color: var(--text-muted);
}

.custom-select-trigger.open .arrow-icon {
  transform: rotate(180deg);
}

.custom-select-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: #111215;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  z-index: 100;
  max-height: 200px;
  overflow-y: auto;
  box-shadow: var(--shadow-soft);
  padding: 0.25rem;
}

.custom-select-option {
  padding: 0.5rem 0.75rem;
  font-size: 0.85rem;
  color: var(--text-helper);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.1s ease;
}

.custom-select-option:hover {
  background: rgba(255, 255, 255, 0.04);
  color: #fff;
}

.custom-select-option.selected {
  background: var(--accent-lavender-muted);
  color: var(--accent-lavender);
  font-weight: 500;
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
