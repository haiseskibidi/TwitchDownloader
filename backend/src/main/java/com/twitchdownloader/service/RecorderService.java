package com.twitchdownloader.service;

import com.twitchdownloader.model.Recording;
import com.twitchdownloader.model.RecordingStatus;
import com.twitchdownloader.model.Streamer;
import com.twitchdownloader.repository.RecordingRepository;
import com.twitchdownloader.websocket.LogWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RecorderService {
    private static final Logger log = LoggerFactory.getLogger(RecorderService.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final RecordingRepository recordingRepository;
    private final SettingService settingService;
    private final LogWebSocketHandler webSocketHandler;

    // Map of active processes keyed by Streamer ID
    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    // Map of active recording database IDs keyed by Streamer ID
    private final Map<Long, Long> activeRecordingIds = new ConcurrentHashMap<>();

    public RecorderService(RecordingRepository recordingRepository,
                           SettingService settingService,
                           LogWebSocketHandler webSocketHandler) {
        this.recordingRepository = recordingRepository;
        this.settingService = settingService;
        this.webSocketHandler = webSocketHandler;
    }

    @PostConstruct
    public void init() {
        cleanupDeadActiveRecordings();
        checkStreamlinkPresence();
    }

    private void checkStreamlinkPresence() {
        try {
            ProcessBuilder pb = new ProcessBuilder("streamlink", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String version = reader.readLine();
                    log.info("Streamlink CLI detected. Version: {}", version);
                }
            } else {
                log.warn("Warning: streamlink CLI returned exit code {} during startup check.", exitCode);
            }
        } catch (IOException | InterruptedException e) {
            log.error("CRITICAL WARNING: 'streamlink' CLI was NOT detected in the system path! " +
                    "The service will NOT be able to record streams. Please install streamlink and add it to your system PATH. " +
                    "Error: {}", e.getMessage());
        }
    }

    /**
     * Finds recordings that were left in the ACTIVE state (e.g. if the app crashed)
     * and updates them to FAILED or COMPLETED depending on file existence.
     */
    private void cleanupDeadActiveRecordings() {

        log.info("Checking for dead active recordings on startup...");
        List<Recording> activeRecordings = recordingRepository.findByStatus(RecordingStatus.ACTIVE);
        for (Recording rec : activeRecordings) {
            log.warn("Found dead active recording ID {} for streamer {}", rec.getId(), rec.getStreamer().getTwitchUsername());
            File file = rec.getFilePath() != null ? new File(rec.getFilePath()) : null;
            if (file != null && file.exists()) {
                rec.setStatus(RecordingStatus.COMPLETED);
                rec.setFileSize(file.length());
            } else {
                rec.setStatus(RecordingStatus.FAILED);
            }
            rec.setEndedAt(LocalDateTime.now());
            recordingRepository.save(rec);
        }
    }

    public synchronized boolean isRecording(Long streamerId) {
        return activeProcesses.containsKey(streamerId);
    }

    public synchronized void startRecording(Streamer streamer, String twitchStreamId, String title) {
        if (isRecording(streamer.getId())) {
            log.info("Streamer {} is already being recorded.", streamer.getTwitchUsername());
            return;
        }

        log.info("Starting recording for streamer: {}", streamer.getTwitchUsername());

        String rootDownloadPath = settingService.getDownloadPath();
        File recordingDir = new File(rootDownloadPath + "/recording");
        File completedDir = new File(rootDownloadPath + "/completed");

        if (!recordingDir.exists() && !recordingDir.mkdirs()) {
            log.error("Failed to create recording directory: {}", recordingDir.getAbsolutePath());
            return;
        }
        if (!completedDir.exists() && !completedDir.mkdirs()) {
            log.error("Failed to create completed directory: {}", completedDir.getAbsolutePath());
            return;
        }

        String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
        // Safe filename replacing special characters
        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = String.format("%s_%s_%s.mp4", streamer.getTwitchUsername(), timestamp, safeTitle);
        if (filename.length() > 100) { // Limit length
            filename = String.format("%s_%s_%s.mp4", streamer.getTwitchUsername(), timestamp, twitchStreamId);
        }

        File tempFile = new File(recordingDir, filename);
        File finalFile = new File(completedDir, filename);

        // Save recording metadata to DB
        Recording recording = Recording.builder()
                .streamer(streamer)
                .twitchStreamId(twitchStreamId)
                .title(title)
                .status(RecordingStatus.ACTIVE)
                .filePath(tempFile.getAbsolutePath())
                .startedAt(LocalDateTime.now())
                .build();
        recording = recordingRepository.save(recording);

        final Long recordingId = recording.getId();
        activeRecordingIds.put(streamer.getId(), recordingId);

        // Build streamlink command
        String quality = settingService.getDownloadQuality();
        List<String> command = List.of(
                "streamlink",
                "twitch.tv/" + streamer.getTwitchUsername(),
                quality,
                "-o", tempFile.getAbsolutePath(),
                "--twitch-disable-ads",
                "--retry-streams", "10"
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            activeProcesses.put(streamer.getId(), process);

            webSocketHandler.broadcastSystemEvent("RECORDING_STARTED", streamer.getTwitchUsername());

            // Thread for reading stdout (logs)
            Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        webSocketHandler.sendLogToSubscribers(streamer.getTwitchUsername(), line);
                    }
                } catch (IOException e) {
                    log.error("Error reading stdout for streamer {}: {}", streamer.getTwitchUsername(), e.getMessage());
                }
            });

            // Thread for reading stderr (errors)
            Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.warn("[Streamlink-Err][{}] {}", streamer.getTwitchUsername(), line);
                        webSocketHandler.sendLogToSubscribers(streamer.getTwitchUsername(), "[ERROR] " + line);
                    }
                } catch (IOException e) {
                    log.error("Error reading stderr for streamer {}: {}", streamer.getTwitchUsername(), e.getMessage());
                }
            });

            // Thread for waiting process completion
            Thread.ofVirtual().start(() -> {
                try {
                    int exitCode = process.waitFor();
                    log.info("Streamlink process for {} exited with code {}", streamer.getTwitchUsername(), exitCode);
                    handleProcessCompletion(streamer.getId(), recordingId, tempFile, finalFile, exitCode);
                } catch (InterruptedException e) {
                    log.error("Recording thread interrupted for streamer {}", streamer.getTwitchUsername());
                    process.destroyForcibly();
                    handleProcessCompletion(streamer.getId(), recordingId, tempFile, finalFile, -1);
                }
            });

        } catch (IOException e) {
            log.error("Failed to start streamlink process for streamer {}: {}", streamer.getTwitchUsername(), e.getMessage());
            recording.setStatus(RecordingStatus.FAILED);
            recording.setEndedAt(LocalDateTime.now());
            recordingRepository.save(recording);
            activeRecordingIds.remove(streamer.getId());
            activeProcesses.remove(streamer.getId());
        }
    }

    private synchronized void handleProcessCompletion(Long streamerId, Long recordingId, File tempFile, File finalFile, int exitCode) {
        activeProcesses.remove(streamerId);
        activeRecordingIds.remove(streamerId);

        Recording recording = recordingRepository.findById(recordingId).orElse(null);
        if (recording == null) {
            log.error("Recording record not found in DB: {}", recordingId);
            return;
        }

        recording.setEndedAt(LocalDateTime.now());

        if (tempFile.exists() && tempFile.length() > 0) {
            try {
                log.info("Moving recorded file to completed directory: {}", finalFile.getAbsolutePath());
                Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                recording.setStatus(RecordingStatus.COMPLETED);
                recording.setFilePath(finalFile.getAbsolutePath());
                recording.setFileSize(finalFile.length());
            } catch (IOException e) {
                log.error("Failed to move file for recording {}: {}", recordingId, e.getMessage());
                // Fallback: keep temporary path but mark as completed
                recording.setStatus(RecordingStatus.COMPLETED);
                recording.setFilePath(tempFile.getAbsolutePath());
                recording.setFileSize(tempFile.length());
            }
        } else {
            log.warn("Recording finished but output file is missing or empty: {}", tempFile.getAbsolutePath());
            recording.setStatus(RecordingStatus.FAILED);
        }

        recordingRepository.save(recording);
        webSocketHandler.broadcastSystemEvent("RECORDING_FINISHED", recording.getStreamer().getTwitchUsername());
    }

    public synchronized boolean stopRecording(Long streamerId) {
        Process process = activeProcesses.get(streamerId);
        if (process == null) {
            return false;
        }

        log.info("Stopping recording process for streamer ID: {}", streamerId);
        process.destroy(); // SIGTERM - allows streamlink to close file cleanly

        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                log.warn("Process did not exit after 5s. Forcing destroy...");
                process.destroyForcibly();
            }
            return true;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            return true;
        }
    }

    public Map<Long, Process> getActiveProcesses() {
        return activeProcesses;
    }

    public Long getActiveRecordingId(Long streamerId) {
        return activeRecordingIds.get(streamerId);
    }
}
