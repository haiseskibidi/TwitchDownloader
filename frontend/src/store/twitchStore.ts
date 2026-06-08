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
    async fetchStreamers() {
      try {
        const res = await fetch('/api/streamers')
        if (res.ok) {
          this.streamers = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch streamers', err)
      }
    },

    async fetchRecordings() {
      try {
        const res = await fetch('/api/recordings')
        if (res.ok) {
          this.recordings = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch recordings', err)
      }
    },

    async fetchStats() {
      try {
        const res = await fetch('/api/system/stats')
        if (res.ok) {
          this.stats = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch stats', err)
      }
    },

    async fetchSettings() {
      try {
        const res = await fetch('/api/settings')
        if (res.ok) {
          this.settings = await res.json()
        }
      } catch (err) {
        console.error('Failed to fetch settings', err)
      }
    },

    async saveSettings(settings: Settings) {
      try {
        const res = await fetch('/api/settings', {
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
        const res = await fetch(`/api/streamers/search?query=${encodeURIComponent(query)}`)
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
        const res = await fetch('/api/streamers', {
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
        const res = await fetch(`/api/streamers/${id}`, { method: 'DELETE' })
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
        const res = await fetch(`/api/streamers/${id}`, {
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
        const res = await fetch(`/api/recordings/${id}`, { method: 'DELETE' })
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
        const res = await fetch(`/api/recordings/${id}/stop`, { method: 'POST' })
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

    connectWebSocket() {
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
        logInfo('WebSocket disconnected. Reconnecting in 5s...')
        this.wsConnected = false
        this.ws = null
        
        this.wsReconnectTimeout = setTimeout(() => {
          this.connectWebSocket()
        }, 5000)
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
