package com.twitchdownloader.service;

import com.twitchdownloader.model.Recording;
import com.twitchdownloader.model.RecordingStatus;
import com.twitchdownloader.repository.RecordingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DiskManagerService {
    private static final Logger log = LoggerFactory.getLogger(DiskManagerService.class);

    private final RecordingRepository recordingRepository;
    private final SettingService settingService;

    public DiskManagerService(RecordingRepository recordingRepository, SettingService settingService) {
        this.recordingRepository = recordingRepository;
        this.settingService = settingService;
    }

    @Scheduled(fixedDelay = 120000) // Runs every 2 minutes
    public void checkFilesAndDiskSpace() {
        checkCompletedRecordingsFileExistence();
        if (settingService.isDiskCleanupEnabled()) {
            performAutoCleanupIfNeeded();
        }
    }

    /**
     * Checks if files for COMPLETED recordings still exist.
     * If they were moved or deleted (e.g. by rclone), updates their status to MOVED_TO_LOCAL.
     */
    public void checkCompletedRecordingsFileExistence() {
        List<Recording> completed = recordingRepository.findByStatus(RecordingStatus.COMPLETED);
        for (Recording rec : completed) {
            if (rec.getFilePath() != null) {
                File file = new File(rec.getFilePath());
                if (!file.exists()) {
                    log.info("File for recording {} ({}) was moved or deleted externally. Updating status to MOVED_TO_LOCAL.",
                            rec.getId(), rec.getTitle());
                    rec.setStatus(RecordingStatus.MOVED_TO_LOCAL);
                    recordingRepository.save(rec);
                }
            }
        }
    }

    /**
     * Deletes the oldest files if free disk space is below the configured threshold.
     */
    public void performAutoCleanupIfNeeded() {
        String path = settingService.getDownloadPath();
        File root = new File(path);
        if (!root.exists()) return;

        double freePercent = getFreeSpacePercentage(root);
        double threshold = settingService.getFreeSpaceThresholdPercent();

        if (freePercent < threshold) {
            log.warn("Free disk space ({}) is below threshold ({}). Running auto-cleanup...",
                    String.format("%.2f%%", freePercent), String.format("%.2f%%", threshold));

            // Find all completed recordings, oldest first
            List<Recording> completed = recordingRepository.findByStatus(RecordingStatus.COMPLETED);
            completed.sort((r1, r2) -> r1.getStartedAt().compareTo(r2.getStartedAt()));

            for (Recording rec : completed) {
                if (rec.getFilePath() != null) {
                    File file = new File(rec.getFilePath());
                    if (file.exists()) {
                        long size = file.length();
                        if (file.delete()) {
                            log.info("Auto-cleanup deleted file: {} ({} MB) to free space.",
                                    file.getName(), size / (1024 * 1024));
                            rec.setStatus(RecordingStatus.MOVED_TO_LOCAL);
                            recordingRepository.save(rec);

                            // Re-evaluate free space
                            freePercent = getFreeSpacePercentage(root);
                            if (freePercent >= threshold) {
                                log.info("Disk space cleared. Free space is now: {}", String.format("%.2f%%", freePercent));
                                break;
                            }
                        } else {
                            log.error("Failed to delete file during auto-cleanup: {}", file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    private double getFreeSpacePercentage(File root) {
        long free = root.getFreeSpace();
        long total = root.getTotalSpace();
        if (total == 0) return 100.0;
        return (double) free / total * 100.0;
    }

    public Map<String, Object> getDiskStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            File root = new File(settingService.getDownloadPath());
            if (!root.exists()) {
                root.mkdirs();
            }
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            long used = total - free;

            stats.put("total", total);
            stats.put("free", free);
            stats.put("used", used);
            stats.put("freePercentage", total > 0 ? (double) free / total * 100.0 : 0.0);
        } catch (Exception e) {
            log.error("Failed to fetch disk stats: {}", e.getMessage());
            stats.put("total", 0L);
            stats.put("free", 0L);
            stats.put("used", 0L);
            stats.put("freePercentage", 0.0);
        }
        return stats;
    }
}
