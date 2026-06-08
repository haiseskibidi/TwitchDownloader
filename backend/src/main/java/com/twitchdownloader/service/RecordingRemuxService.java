package com.twitchdownloader.service;

import com.twitchdownloader.model.Recording;
import com.twitchdownloader.model.RecordingStatus;
import com.twitchdownloader.repository.RecordingRepository;
import com.twitchdownloader.websocket.LogWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RecordingRemuxService {
    private static final Logger log = LoggerFactory.getLogger(RecordingRemuxService.class);

    private final RecordingRepository recordingRepository;
    private final LogWebSocketHandler webSocketHandler;
    private boolean isFfmpegAvailable = false;

    public RecordingRemuxService(RecordingRepository recordingRepository, LogWebSocketHandler webSocketHandler) {
        this.recordingRepository = recordingRepository;
        this.webSocketHandler = webSocketHandler;
        checkFfmpegPresence();
    }

    private void checkFfmpegPresence() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                isFfmpegAvailable = true;
                log.info("FFmpeg CLI detected and is available for remuxing streams.");
            } else {
                log.warn("Warning: FFmpeg CLI returned exit code {} during startup check.", exitCode);
            }
        } catch (Exception e) {
            log.error("FFmpeg CLI was NOT detected in the system path! " +
                    "Web player playback of live streams may fail due to lack of MPEG-TS support in browsers. " +
                    "Please install ffmpeg.");
        }
    }

    public boolean isFfmpegAvailable() {
        return isFfmpegAvailable;
    }

    public static boolean isMpegTs(File file) {
        if (!file.exists() || file.length() < 188) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[8];
            raf.readFully(header);
            // MP4 starts with ftyp at offset 4
            if (header.length >= 8 && header[4] == 0x66 && header[5] == 0x74 && header[6] == 0x79 && header[7] == 0x70) {
                return false;
            }
            // Check for MPEG-TS sync byte 0x47
            if (header[0] == 0x47) {
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean lacksFastStart(File file) {
        if (!file.exists() || file.length() < 100) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[8];
            raf.readFully(header);
            // Verify it is an MP4 (starts with ftyp at offset 4)
            if (!(header[4] == 0x66 && header[5] == 0x74 && header[6] == 0x79 && header[7] == 0x70)) {
                return false; // Not a valid MP4 file
            }
            // Read ftyp size (first 4 bytes as big-endian int)
            int ftypSize = ((header[0] & 0xFF) << 24) |
                           ((header[1] & 0xFF) << 16) |
                           ((header[2] & 0xFF) << 8)  |
                           (header[3] & 0xFF);
            if (ftypSize <= 0 || ftypSize > file.length() - 8) {
                return true; // Corrupted or unusual structure
            }
            raf.seek(ftypSize);
            byte[] nextBoxHeader = new byte[8];
            raf.readFully(nextBoxHeader);
            String boxType = new String(nextBoxHeader, 4, 4);
            if ("mdat".equals(boxType)) {
                return true; // moov box is at the end!
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean remuxMpegTsToMp4(File inputFile, File outputFile) {
        if (!isFfmpegAvailable) {
            log.warn("Cannot remux: FFmpeg is not available.");
            return false;
        }
        log.info("Starting remuxing from {} to {}", inputFile.getName(), outputFile.getName());
        long startTime = System.currentTimeMillis();
        try {
            List<String> command = List.of(
                    "ffmpeg", "-y",
                    "-i", inputFile.getAbsolutePath(),
                    "-c", "copy",
                    "-map", "0",
                    "-movflags", "+faststart",
                    outputFile.getAbsolutePath()
            );
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Consume process output in virtual thread to prevent hanging
            Thread.ofVirtual().start(() -> {
                try (var is = process.getInputStream()) {
                    is.transferTo(OutputStream.nullOutputStream());
                } catch (IOException e) {
                    // Ignore
                }
            });

            boolean finished = process.waitFor(30, TimeUnit.MINUTES); // Limit to 30 mins
            if (!finished) {
                log.error("FFmpeg remuxing timed out for file: {}", inputFile.getName());
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("FFmpeg remuxing completed successfully in {} ms for file: {}",
                        (System.currentTimeMillis() - startTime), outputFile.getName());
                return true;
            } else {
                log.error("FFmpeg remuxing failed with exit code: {} for file: {}", exitCode, inputFile.getName());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to execute FFmpeg remuxing: {}", e.getMessage(), e);
            return false;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startStartupRemuxTask() {
        if (!isFfmpegAvailable) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            log.info("Scanning for existing MPEG-TS or non-web-optimized MP4 recordings to remux...");
            List<Recording> completed = recordingRepository.findByStatus(RecordingStatus.COMPLETED);
            int convertedCount = 0;
            for (Recording rec : completed) {
                if (rec.getFilePath() != null) {
                    File file = new File(rec.getFilePath());
                    if (file.exists() && (isMpegTs(file) || lacksFastStart(file))) {
                        log.info("Found recording to optimize/convert: {} (ID: {})", file.getName(), rec.getId());
                        File tempTsFile = new File(file.getParentFile(), "temp_" + System.currentTimeMillis() + "_" + file.getName() + ".ts");
                        try {
                            // Rename original to temp ts file
                            Files.move(file.toPath(), tempTsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            boolean success = remuxMpegTsToMp4(tempTsFile, file);
                            if (success) {
                                // Update file size in DB
                                rec.setFileSize(file.length());
                                recordingRepository.save(rec);
                                tempTsFile.delete();
                                convertedCount++;
                                log.info("Successfully converted recording ID: {} to Web-compatible MP4", rec.getId());
                                webSocketHandler.broadcastSystemEvent("RECORDING_UPDATED", rec.getId().toString());
                            } else {
                                // Rollback: rename temp ts file back to original mp4 path
                                Files.move(tempTsFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                log.error("Failed to convert recording ID: {}. Restored original file.", rec.getId());
                            }
                        } catch (IOException e) {
                            log.error("IO error while preparing to convert file for recording ID: {}: {}", rec.getId(), e.getMessage());
                            if (tempTsFile.exists()) {
                                try {
                                    Files.move(tempTsFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                } catch (IOException ignored) {}
                            }
                        }
                    }
                }
            }
            if (convertedCount > 0) {
                log.info("Startup MPEG-TS scanning and remuxing task finished. Converted {} files.", convertedCount);
            } else {
                log.info("Startup MPEG-TS scanning finished. No files needed conversion.");
            }
        });
    }
}
