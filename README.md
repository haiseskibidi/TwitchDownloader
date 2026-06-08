# Twitch Auto-Recorder 🎥

A self-hosted service that monitors Twitch channels, automatically records live streams using `streamlink`, and supports automatic file transfer and cleanup via `rclone` to free up server space.

---

## 🚀 Technology Stack
* **Backend**: Java 21, Spring Boot 3.3, JPA Hibernate, SQLite, WebSockets
* **Frontend**: Vue 3, TypeScript, Vite, Pinia, Custom CSS (Charcoal & Lavender)
* **Stream Capture**: `streamlink` CLI

---

## ⚙️ Features
* **Zero Configuration**: Uses public Twitch GraphQL API endpoints (works without Twitch Developer credentials).
* **Live Autocomplete**: Real-time channel search directly from Twitch when adding channels.
* **Disk Space Management**: Monitors space, auto-cleans completed records when disk threshold is reached, and tracks files transferred to local storage.
* **Console Logs Dialog**: Live console output streaming via WebSockets.

---

## 🛠️ Getting Started

### Option 1: Docker Compose (Production / Server)
Automatically sets up backend, frontend, database, and streamlink.

1. Run the compose command:
   ```bash
   docker compose up -d --build
   ```
2. Access the service:
   * **Web UI**: `http://<server-ip>:80`
   * **Backend API**: `http://<server-ip>:8080`

---

### Option 2: Local Development

#### Prerequisites
* **Streamlink CLI** installed and available in system PATH.
* **Java 21** & **Node.js 22** or higher.

#### 1. Start Backend
```bash
cd backend
./gradlew bootRun
```
*Port: `8080`. SQLite database created at `backend/db/twitch_downloader.db`.*

#### 2. Start Frontend
```bash
cd frontend
npm install
npm run dev
```
*Port: `5173`. API and WebSocket requests are proxied automatically.*

---

## 🔗 Local PC Sync with `rclone`

To automatically transfer recorded files from VPS to your local PC:

1. Install `rclone` on your local PC.
2. Configure SSH/SFTP connection to your VPS:
   ```bash
   rclone config
   ```
   *Create a new remote (e.g., `vps_server`), choose **SFTP**, enter your VPS IP, SSH username (e.g., `root`), and password/SSH key path.*
3. Run the synchronization command (e.g., in a scheduled task or script):
   ```bash
   rclone move vps_server:/app/downloads/completed /your/local/path --delete-empty-src-dirs
   ```

### How Cleanup Works
* `rclone move` downloads the completed streams and deletes them from the server.
* Every 2 minutes, the backend detects if any completed files are missing from the download directory and changes their DB status to `MOVED_TO_LOCAL`. This frees up server space for subsequent recordings.
