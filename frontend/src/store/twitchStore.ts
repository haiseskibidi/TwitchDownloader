import { defineStore } from 'pinia'

export interface Streamer {
  id: number
  twitchUsername: string
  displayName: string
  twitchId: string
  profileImageUrl?: string
  isActive: boolean
  addedAt: string
  isRecording: boolean
}

export interface Recording {
  id: number
  streamer: {
    id: number
    twitchUsername: string
    displayName: string
  }
  twitchStreamId: string
  title: string
  status: 'ACTIVE' | 'COMPLETED' | 'MOVED_TO_LOCAL' | 'DELETED' | 'FAILED'
  filePath: string
  startedAt: string
  endedAt: string
  fileSize: number
}

export interface SystemStats {
  total: number
  free: number
  used: number
  freePercentage: number
}

export interface Settings {
  twitch_client_id: string
  twitch_client_secret: string
  download_path: string
  download_quality: string
  disk_cleanup_enabled: string
  free_space_threshold_percent: string
}

export interface TwitchSearchChannel {
  id: string
  broadcaster_login: string
  display_name: string
  thumbnail_url: string
  is_live: boolean
}

export const useTwitchStore = defineStore('twitch', {
  state: () => ({
    // Auth State
    user: null as { id: number, username: string, role: string } | null,
    isAuthenticated: false,
    checkingAuth: true,
    hasSystemUsers: true,

    streamers: [] as Streamer[],
    recordings: [] as Recording[],
    stats: {
      total: 0,
      free: 0,
      used: 0,
      freePercentage: 100
    } as SystemStats,
    settings: {
      twitch_client_id: '',
      twitch_client_secret: '',
      download_path: 'downloads',
      download_quality: 'best',
      disk_cleanup_enabled: 'true',
      free_space_threshold_percent: '10'
    } as Settings,
    
    // Autocomplete search channel results
    searchChannelResults: [] as TwitchSearchChannel[],
    
    // Logs map: streamer username (lowercase) -> array of string lines
    activeLogs: {} as Record<string, string[]>,
    
    activeTab: 'dashboard' as 'dashboard' | 'archive' | 'settings',
    selectedStreamerLogs: '' as string, // Username for active log terminal
    
    wsConnected: false,
    ws: null as WebSocket | null,
    wsReconnectTimeout: null as any
  }),

  actions: {
    // API Request wrapper that handles 401 Unauthorized globally
    async apiFetch(url: string, options?: RequestInit) {
      const res = await fetch(url, options)
      if (res.status === 401) {
        this.isAuthenticated = false
        this.user = null
        if (this.ws) {
          this.ws.close()
          this.ws = null
        }
        if (this.wsReconnectTimeout) {
          clearTimeout(this.wsReconnectTimeout)
          this.wsReconnectTimeout = null
        }
        this.wsConnected = false
        throw new Error('Unauthorized')
      }
      return res
    },

    // Authentication Actions
    async checkAuth() {
      this.checkingAuth = true
      try {
        const res = await fetch('/api/auth/me')
        if (res.ok) {
          this.user = await res.json()
          this.isAuthenticated = true
          if (!this.wsConnected && !this.ws) {
            this.connectWebSocket()
          }
        } else {
          this.user = null
          this.isAuthenticated = false
        }
      } catch (err) {
        console.error('Failed to check auth', err)
        this.user = null
        this.isAuthenticated = false
      } finally {
        this.checkingAuth = false
      }
    },

    async checkSystemUsers() {
      try {
        const res = await fetch('/api/auth/has-users')
        if (res.ok) {
          const contentType = res.headers.get('content-type')
          if (contentType && contentType.includes('application/json')) {
            const data = await res.json()
            this.hasSystemUsers = data.hasUsers
          }
        }
      } catch (err) {
        console.error('Failed to check system users', err)
      }
    },

    async login(username: string, password: string) {
      try {
        const res = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        })
        if (res.ok) {
          this.user = await res.json()
          this.isAuthenticated = true
          this.connectWebSocket()
          
          // Fetch operational data
          this.fetchStreamers()
          this.fetchRecordings()
          this.fetchStats()
          this.fetchSettings()
          return { success: true }
        } else {
          let errorMsg = 'Неверное имя пользователя или пароль'
          try {
            const data = await res.json()
            errorMsg = data.error || errorMsg
          } catch (e) {
            errorMsg = `Ошибка сервера (${res.status}): ${res.statusText || 'Bad Gateway'}`
          }
          return { success: false, error: errorMsg }
        }
      } catch (err) {
        console.error('Login error', err)
        return { success: false, error: 'Ошибка сети при попытке входа' }
      }
    },

    async register(username: string, password: string) {
      try {
        const res = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username, password })
        })
        if (res.ok) {
          this.user = await res.json()
          this.isAuthenticated = true
          this.hasSystemUsers = true
          this.connectWebSocket()

          // Fetch operational data
          this.fetchStreamers()
          this.fetchRecordings()
          this.fetchStats()
          this.fetchSettings()
          return { success: true }
        } else {
          let errorMsg = 'Ошибка при регистрации'
          try {
            const data = await res.json()
            errorMsg = data.error || errorMsg
          } catch (e) {
            errorMsg = `Ошибка сервера (${res.status}): ${res.statusText || 'Bad Gateway'}`
          }
          return { success: false, error: errorMsg }
        }
      } catch (err) {
        console.error('Registration error', err)
        return { success: false, error: 'Ошибка сети при попытке регистрации' }
      }
    },

    async logout() {
      try {
        await fetch('/api/auth/logout', { method: 'POST' })
      } catch (err) {
        console.error('Logout error', err)
      } finally {
        this.user = null
        this.isAuthenticated = false
        if (this.ws) {
          this.ws.close()
          this.ws = null
        }
        if (this.wsReconnectTimeout) {
          clearTimeout(this.wsReconnectTimeout)
          this.wsReconnectTimeout = null
        }
        this.wsConnected = false
      }
    },

    // Operational Actions
    async fetchStreamers() {
      try {
        const res = await this.apiFetch('/api/streamers')
        if (res.ok) {
          this.streamers = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch streamers', err)
      }
    },

    async fetchRecordings() {
      try {
        const res = await this.apiFetch('/api/recordings')
        if (res.ok) {
          this.recordings = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch recordings', err)
      }
    },

    async fetchStats() {
      try {
        const res = await this.apiFetch('/api/system/stats')
        if (res.ok) {
          this.stats = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch stats', err)
      }
    },

    async fetchSettings() {
      try {
        const res = await this.apiFetch('/api/settings')
        if (res.ok) {
          this.settings = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch settings', err)
      }
    },

    async saveSettings(settings: Settings) {
      try {
        const res = await this.apiFetch('/api/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(settings)
        })
        if (res.ok) {
          await this.fetchSettings()
          await this.fetchStats()
          return true
        }
      } catch (err) {
        console.error('Failed to save settings', err)
      }
      return false
    },

    async searchChannels(query: string) {
      if (!query.trim()) {
        this.searchChannelResults = []
        return
      }
      try {
        const res = await this.apiFetch(`/api/streamers/search?query=${encodeURIComponent(query)}`)
        if (res.ok) {
          this.searchChannelResults = await res.json()
        }
      } catch (err) {
        console.error('Failed to search channels', err)
      }
    },

    async addStreamer(username: string, details?: { displayName: string, twitchId: string, profileImageUrl: string }) {
      try {
        const bodyPayload: Record<string, string> = { username }
        if (details) {
          bodyPayload.displayName = details.displayName
          bodyPayload.twitchId = details.twitchId
          bodyPayload.profileImageUrl = details.profileImageUrl
        }
        const res = await this.apiFetch('/api/streamers', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(bodyPayload)
        })
        if (res.ok) {
          await this.fetchStreamers()
          return { success: true }
        } else {
          const data = await res.json()
          return { success: false, error: data.error || 'Failed to add streamer' }
        }
      } catch (err) {
        console.error('Failed to add streamer', err)
        return { success: false, error: 'Network error occurred' }
      }
    },

    async deleteStreamer(id: number) {
      try {
        const res = await this.apiFetch(`/api/streamers/${id}`, { method: 'DELETE' })
        if (res.ok) {
          await this.fetchStreamers()
          return true
        }
      } catch (err) {
        console.error('Failed to delete streamer', err)
      }
      return false
    },

    async toggleStreamer(id: number, isActive: boolean) {
      try {
        const res = await this.apiFetch(`/api/streamers/${id}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ isActive })
        })
        if (res.ok) {
          await this.fetchStreamers()
          return true
        }
      } catch (err) {
        console.error('Failed to toggle streamer', err)
      }
      return false
    },

    async deleteRecording(id: number) {
      try {
        const res = await this.apiFetch(`/api/recordings/${id}`, { method: 'DELETE' })
        if (res.ok) {
          await this.fetchRecordings()
          await this.fetchStats()
          return true
        }
      } catch (err) {
        console.error('Failed to delete recording', err)
      }
      return false
    },

    async stopRecording(id: number) {
      try {
        const res = await this.apiFetch(`/api/recordings/${id}/stop`, { method: 'POST' })
        if (res.ok) {
          await this.fetchRecordings()
          await this.fetchStreamers()
          return true
        }
      } catch (err) {
        console.error('Failed to stop recording', err)
      }
      return false
    },

    async splitRecording(id: number) {
      try {
        const res = await this.apiFetch(`/api/recordings/${id}/split`, { method: 'POST' })
        if (res.ok) {
          await this.fetchRecordings()
          await this.fetchStreamers()
          return true
        }
      } catch (err) {
        console.error('Failed to split recording', err)
      }
      return false
    },

    connectWebSocket() {
      if (!this.isAuthenticated) {
        logInfo('Skipping WebSocket connection: not authenticated')
        return
      }
      if (this.ws) {
        this.ws.close()
      }

      const loc = window.location
      const protocol = loc.protocol === 'https:' ? 'wss:' : 'ws:'
      const wsUrl = `${protocol}//${loc.host}/ws/logs`

      logInfo(`Connecting to WebSocket: ${wsUrl}`)
      const socket = new WebSocket(wsUrl)

      socket.onopen = () => {
        logInfo('WebSocket connected.')
        this.wsConnected = true
        if (this.wsReconnectTimeout) {
          clearTimeout(this.wsReconnectTimeout)
          this.wsReconnectTimeout = null
        }
        
        // If a streamer is selected for logs, subscribe immediately
        if (this.selectedStreamerLogs) {
          socket.send(`SUBSCRIBE:${this.selectedStreamerLogs}`)
        }
      }

      socket.onmessage = (event) => {
        const data = event.data as string
        
        if (data.startsWith('LOG:')) {
          // Format: LOG:username:logLine
          const firstColon = data.indexOf(':')
          const secondColon = data.indexOf(':', firstColon + 1)
          if (firstColon !== -1 && secondColon !== -1) {
            const username = data.substring(firstColon + 1, secondColon).toLowerCase()
            const logLine = data.substring(secondColon + 1)
            
            if (!this.activeLogs[username]) {
              this.activeLogs[username] = []
            }
            this.activeLogs[username].push(logLine)
            
            // Limit to 500 lines to prevent tab crashing
            if (this.activeLogs[username].length > 500) {
              this.activeLogs[username].shift()
            }
          }
        } else if (data.startsWith('EVENT:')) {
          // Format: EVENT:eventType:payload
          const parts = data.split(':')
          const eventType = parts[1]
          logInfo(`Received System Event: ${eventType}`)
          
          // Refresh state on recording events
          this.fetchStreamers()
          this.fetchRecordings()
          this.fetchStats()
        }
      }

      socket.onclose = () => {
        logInfo('WebSocket disconnected.')
        this.wsConnected = false
        this.ws = null
        
        if (this.isAuthenticated) {
          logInfo('Reconnecting in 5s...')
          this.wsReconnectTimeout = setTimeout(() => {
            this.connectWebSocket()
          }, 5000)
        }
      }

      socket.onerror = (err) => {
        console.error('WebSocket error:', err)
        socket.close()
      }

      this.ws = socket
    },

    selectStreamerForLogs(username: string) {
      this.selectedStreamerLogs = username
      if (this.ws && this.wsConnected) {
        if (username) {
          this.activeLogs[username.toLowerCase()] = [] // clear previous logs
          this.ws.send(`SUBSCRIBE:${username}`)
        } else {
          this.ws.send('UNSUBSCRIBE')
        }
      }
    }
  }
})

function logInfo(msg: string) {
  console.log(`[Store] ${msg}`)
}
