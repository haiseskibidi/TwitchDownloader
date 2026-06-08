package com.twitchdownloader.controller;

import com.twitchdownloader.model.Recording;
import com.twitchdownloader.model.RecordingStatus;
import com.twitchdownloader.model.Streamer;
import com.twitchdownloader.repository.RecordingRepository;
import com.twitchdownloader.repository.UserStreamerRepository;
import com.twitchdownloader.service.RecorderService;
import com.twitchdownloader.scheduler.TwitchScheduler;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/recordings")
@CrossOrigin(origins = "*")
public class RecordingController {
    private static final Logger log = LoggerFactory.getLogger(RecordingController.class);

    private final RecordingRepository recordingRepository;
    private final RecorderService recorderService;
    private final TwitchScheduler twitchScheduler;
    private final UserStreamerRepository userStreamerRepository;

    public RecordingController(RecordingRepository recordingRepository,
                               RecorderService recorderService,
                               TwitchScheduler twitchScheduler,
                               UserStreamerRepository userStreamerRepository) {
        this.recordingRepository = recordingRepository;
        this.recorderService = recorderService;
        this.twitchScheduler = twitchScheduler;
        this.userStreamerRepository = userStreamerRepository;
    }

    @GetMapping
    public List<Recording> getAllRecordings(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return List.of();
        }
        List<Streamer> streamers = userStreamerRepository.findStreamersByUserId(userId);
        if (streamers.isEmpty()) {
            return List.of();
        }
        return recordingRepository.findByStreamerInOrderByStartedAtDesc(streamers);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRecording(@PathVariable Long id) {
        Optional<Recording> recordingOpt = recordingRepository.findById(id);
        if (recordingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Recording recording = recordingOpt.get();
        if (recording.getStatus() == RecordingStatus.ACTIVE) {
            recorderService.stopRecording(recording.getStreamer().getId());
        }

        // Delete file from disk if present
        if (recording.getFilePath() != null) {
            File file = new File(recording.getFilePath());
            if (file.exists()) {
                if (file.delete()) {
                    log.info("Deleted file: {}", file.getAbsolutePath());
                } else {
                    log.error("Failed to delete file: {}", file.getAbsolutePath());
                }
            }
        }

        recordingRepository.delete(recording);
        return ResponseEntity.ok(Map.of("message", "Recording and file deleted successfully"));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stopRecording(@PathVariable Long id) {
        Optional<Recording> recordingOpt = recordingRepository.findById(id);
        if (recordingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Recording recording = recordingOpt.get();
        if (recording.getStatus() != RecordingStatus.ACTIVE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recording is not active"));
        }

        boolean stopped = recorderService.stopRecording(recording.getStreamer().getId());
        if (stopped) {
            return ResponseEntity.ok(Map.of("message", "Recording stopped successfully"));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to stop recording process"));
        }
    }

    @PostMapping("/{id}/split")
    public ResponseEntity<?> splitRecording(@PathVariable Long id) {
        Optional<Recording> recordingOpt = recordingRepository.findById(id);
        if (recordingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Recording recording = recordingOpt.get();
        if (recording.getStatus() != RecordingStatus.ACTIVE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recording is not active"));
        }

        boolean stopped = recorderService.stopRecording(recording.getStreamer().getId());
        if (stopped) {
            // Trigger stream check immediately in a virtual thread to restart recording if online
            Thread.ofVirtual().start(() -> {
                try {
                    // Wait 2 seconds for the previous process to finish writing and clean up
                    java.util.concurrent.TimeUnit.SECONDS.sleep(2);
                    twitchScheduler.checkStreams();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return ResponseEntity.ok(Map.of("message", "Recording split triggered successfully"));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to stop recording process"));
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadRecording(@PathVariable Long id) {
        Optional<Recording> recordingOpt = recordingRepository.findById(id);
        if (recordingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Recording recording = recordingOpt.get();
        if (recording.getFilePath() == null) {
            return ResponseEntity.badRequest().build();
        }

        File file = new File(recording.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<ResourceRegion> streamRecording(@PathVariable Long id, @RequestHeader HttpHeaders headers) {
        Optional<Recording> recordingOpt = recordingRepository.findById(id);
        if (recordingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Recording recording = recordingOpt.get();
        if (recording.getFilePath() == null) {
            return ResponseEntity.badRequest().build();
        }

        File file = new File(recording.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        try {
            long contentLength = resource.contentLength();
            List<HttpRange> ranges = headers.getRange();
            ResourceRegion region;
            if (!ranges.isEmpty()) {
                HttpRange range = ranges.get(0);
                long start = range.getRangeStart(contentLength);
                long end = range.getRangeEnd(contentLength);
                long rangeLength = Math.min(1024 * 1024 * 2L, end - start + 1); // 2MB chunks
                region = new ResourceRegion(resource, start, rangeLength);
            } else {
                long rangeLength = Math.min(1024 * 1024 * 2L, contentLength);
                region = new ResourceRegion(resource, 0, rangeLength);
            }
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .body(region);
        } catch (IOException e) {
            log.error("Failed to read file size or stream file for recording {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
