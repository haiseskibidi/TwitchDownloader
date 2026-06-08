package com.twitchdownloader.controller;

import com.twitchdownloader.dto.TwitchUser;
import com.twitchdownloader.dto.TwitchSearchChannel;
import com.twitchdownloader.dto.TwitchStream;
import com.twitchdownloader.model.Streamer;
import com.twitchdownloader.model.User;
import com.twitchdownloader.model.UserStreamer;
import com.twitchdownloader.repository.StreamerRepository;
import com.twitchdownloader.repository.UserRepository;
import com.twitchdownloader.repository.UserStreamerRepository;
import com.twitchdownloader.service.RecorderService;
import com.twitchdownloader.service.TwitchClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/streamers")
@CrossOrigin(origins = "*")
public class StreamerController {
    private final StreamerRepository streamerRepository;
    private final TwitchClientService twitchClientService;
    private final RecorderService recorderService;
    private final UserStreamerRepository userStreamerRepository;
    private final UserRepository userRepository;

    public StreamerController(StreamerRepository streamerRepository,
                              TwitchClientService twitchClientService,
                              RecorderService recorderService,
                              UserStreamerRepository userStreamerRepository,
                              UserRepository userRepository) {
        this.streamerRepository = streamerRepository;
        this.twitchClientService = twitchClientService;
        this.recorderService = recorderService;
        this.userStreamerRepository = userStreamerRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAllStreamers(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        List<UserStreamer> userStreamers = userStreamerRepository.findByUserId(userId);
        List<Map<String, Object>> result = userStreamers.stream().map(us -> {
            Streamer s = us.getStreamer();
            String avatar = s.getProfileImageUrl();
            String displayName = s.getDisplayName();
            String twitchId = s.getTwitchId();
            
            if (avatar == null || avatar.trim().isEmpty() || twitchId == null || twitchId.trim().isEmpty()) {
                Optional<TwitchUser> userOpt = twitchClientService.getUserInfo(s.getTwitchUsername());
                if (userOpt.isPresent()) {
                    TwitchUser u = userOpt.get();
                    s.setProfileImageUrl(u.profileImageUrl());
                    s.setDisplayName(u.displayName());
                    s.setTwitchId(u.id());
                    streamerRepository.save(s);
                    avatar = u.profileImageUrl();
                    displayName = u.displayName();
                    twitchId = u.id();
                }
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("twitchUsername", s.getTwitchUsername());
            map.put("displayName", displayName != null ? displayName : s.getDisplayName());
            map.put("twitchId", twitchId);
            map.put("profileImageUrl", avatar);
            map.put("isActive", us.isActive());
            map.put("addedAt", s.getAddedAt());
            map.put("isRecording", recorderService.isRecording(s.getId()));
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> addStreamer(HttpSession session, @RequestBody Map<String, String> body) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }
        User user = userOpt.get();

        String username = body.get("username");
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }

        String cleanedUsername = username.trim().toLowerCase();
        
        // Find or create global streamer
        Streamer streamer;
        Optional<Streamer> existingStreamer = streamerRepository.findByTwitchUsernameIgnoreCase(cleanedUsername);
        if (existingStreamer.isPresent()) {
            streamer = existingStreamer.get();
            // Check if user is already tracking this streamer
            Optional<UserStreamer> existingLink = userStreamerRepository.findByUserAndStreamer(user, streamer);
            if (existingLink.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Streamer already added"));
            }
        } else {
            // Fill streamer details
            String displayName = body.get("displayName");
            String twitchId = body.get("twitchId");
            String profileImageUrl = body.get("profileImageUrl");

            if (displayName != null && !displayName.trim().isEmpty()) {
                streamer = Streamer.builder()
                        .twitchUsername(cleanedUsername)
                        .displayName(displayName)
                        .twitchId(twitchId)
                        .profileImageUrl(profileImageUrl)
                        .isActive(true)
                        .build();
            } else if (!twitchClientService.isConfigured()) {
                streamer = Streamer.builder()
                        .twitchUsername(cleanedUsername)
                        .displayName(username)
                        .isActive(true)
                        .build();
            } else {
                Optional<TwitchUser> twitchUserOpt = twitchClientService.getUserInfo(cleanedUsername);
                if (twitchUserOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Twitch channel not found."));
                }
                TwitchUser twitchUser = twitchUserOpt.get();
                streamer = Streamer.builder()
                        .twitchUsername(twitchUser.login())
                        .displayName(twitchUser.displayName())
                        .twitchId(twitchUser.id())
                        .profileImageUrl(twitchUser.profileImageUrl())
                        .isActive(true)
                        .build();
            }
            streamer = streamerRepository.save(streamer);
        }

        // Link user and streamer
        UserStreamer userStreamer = UserStreamer.builder()
                .user(user)
                .streamer(streamer)
                .isActive(true)
                .build();
        userStreamerRepository.save(userStreamer);

        tryStartRecordingIfLive(streamer);

        Map<String, Object> map = new HashMap<>();
        map.put("id", streamer.getId());
        map.put("twitchUsername", streamer.getTwitchUsername());
        map.put("displayName", streamer.getDisplayName());
        map.put("twitchId", streamer.getTwitchId());
        map.put("profileImageUrl", streamer.getProfileImageUrl());
        map.put("isActive", true);
        map.put("addedAt", streamer.getAddedAt());
        map.put("isRecording", recorderService.isRecording(streamer.getId()));

        return ResponseEntity.ok(map);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStreamer(HttpSession session, @PathVariable Long id) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Optional<UserStreamer> linkOpt = userStreamerRepository.findByUserIdAndStreamerId(userId, id);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserStreamer link = linkOpt.get();
        Streamer streamer = link.getStreamer();

        userStreamerRepository.delete(link);

        // If no other active users are tracking this streamer, stop recording
        boolean isStillTracked = userStreamerRepository.findByStreamer(streamer).stream()
                .anyMatch(UserStreamer::isActive);
        if (!isStillTracked && recorderService.isRecording(streamer.getId())) {
            recorderService.stopRecording(streamer.getId());
        }

        return ResponseEntity.ok(Map.of("message", "Streamer deleted successfully"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> toggleStreamer(HttpSession session, @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Optional<UserStreamer> linkOpt = userStreamerRepository.findByUserIdAndStreamerId(userId, id);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserStreamer link = linkOpt.get();
        Boolean active = body.get("isActive");
        if (active != null) {
            link.setActive(active);
            userStreamerRepository.save(link);

            Streamer streamer = link.getStreamer();
            boolean isStillTracked = userStreamerRepository.findByStreamer(streamer).stream()
                    .anyMatch(UserStreamer::isActive);
            
            if (active) {
                // If it goes active and is not recording, check if live to start recording immediately
                if (!recorderService.isRecording(streamer.getId())) {
                    tryStartRecordingIfLive(streamer);
                }
            } else {
                // If disabled and no other user tracks it, stop recording
                if (!isStillTracked && recorderService.isRecording(streamer.getId())) {
                    recorderService.stopRecording(streamer.getId());
                }
            }
        }

        Streamer s = link.getStreamer();
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("twitchUsername", s.getTwitchUsername());
        map.put("displayName", s.getDisplayName());
        map.put("twitchId", s.getTwitchId());
        map.put("profileImageUrl", s.getProfileImageUrl());
        map.put("isActive", link.isActive());
        map.put("addedAt", s.getAddedAt());
        map.put("isRecording", recorderService.isRecording(s.getId()));

        return ResponseEntity.ok(map);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTwitchChannels(@RequestParam String query) {
        return ResponseEntity.ok(twitchClientService.searchChannels(query));
    }

    private void tryStartRecordingIfLive(Streamer streamer) {
        try {
            List<TwitchStream> streams = twitchClientService.getStreamsInfo(
                    List.of(streamer.getTwitchUsername()));
            if (!streams.isEmpty()) {
                TwitchStream stream = streams.get(0);
                recorderService.startRecording(streamer, stream.id(), stream.title());
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(StreamerController.class)
                    .warn("Could not immediately start recording for {}: {}",
                            streamer.getTwitchUsername(), e.getMessage());
        }
    }
}
