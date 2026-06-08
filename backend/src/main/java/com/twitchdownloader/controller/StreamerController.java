package com.twitchdownloader.controller;

import com.twitchdownloader.dto.TwitchUser;
import com.twitchdownloader.dto.TwitchSearchChannel;

import com.twitchdownloader.model.Streamer;
import com.twitchdownloader.repository.StreamerRepository;
import com.twitchdownloader.service.RecorderService;
import com.twitchdownloader.service.TwitchClientService;
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

    public StreamerController(StreamerRepository streamerRepository,
                              TwitchClientService twitchClientService,
                              RecorderService recorderService) {
        this.streamerRepository = streamerRepository;
        this.twitchClientService = twitchClientService;
        this.recorderService = recorderService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllStreamers() {
        List<Streamer> streamers = streamerRepository.findAll();
        return streamers.stream().map(s -> {
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
            map.put("isActive", s.isActive());
            map.put("addedAt", s.getAddedAt());

            map.put("isRecording", recorderService.isRecording(s.getId()));
            return map;
        }).toList();
    }

    @PostMapping
    public ResponseEntity<?> addStreamer(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }

        String cleanedUsername = username.trim().toLowerCase();
        Optional<Streamer> existing = streamerRepository.findByTwitchUsernameIgnoreCase(cleanedUsername);
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Streamer already added"));
        }

        // Check if pre-filled details are passed from autocompletion
        String displayName = body.get("displayName");
        String twitchId = body.get("twitchId");
        String profileImageUrl = body.get("profileImageUrl");

        if (displayName != null && !displayName.trim().isEmpty()) {
            Streamer streamer = Streamer.builder()
                    .twitchUsername(cleanedUsername)
                    .displayName(displayName)
                    .twitchId(twitchId)
                    .profileImageUrl(profileImageUrl)
                    .isActive(true)
                    .build();
            streamerRepository.save(streamer);
            return ResponseEntity.ok(streamer);
        }

        if (!twitchClientService.isConfigured()) {
            Streamer streamer = Streamer.builder()
                    .twitchUsername(cleanedUsername)
                    .displayName(username)
                    .isActive(true)
                    .build();
            streamerRepository.save(streamer);
            return ResponseEntity.ok(streamer);
        }

        Optional<TwitchUser> twitchUserOpt = twitchClientService.getUserInfo(cleanedUsername);
        if (twitchUserOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Twitch channel not found. Make sure Twitch credentials are correct."));
        }

        TwitchUser twitchUser = twitchUserOpt.get();
        Streamer streamer = Streamer.builder()
                .twitchUsername(twitchUser.login())
                .displayName(twitchUser.displayName())
                .twitchId(twitchUser.id())
                .profileImageUrl(twitchUser.profileImageUrl())
                .isActive(true)
                .build();

        streamerRepository.save(streamer);
        return ResponseEntity.ok(streamer);

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStreamer(@PathVariable Long id) {
        Optional<Streamer> streamerOpt = streamerRepository.findById(id);
        if (streamerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Streamer streamer = streamerOpt.get();
        if (recorderService.isRecording(streamer.getId())) {
            recorderService.stopRecording(streamer.getId());
        }

        streamerRepository.delete(streamer);
        return ResponseEntity.ok(Map.of("message", "Streamer deleted successfully"));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> toggleStreamer(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Optional<Streamer> streamerOpt = streamerRepository.findById(id);
        if (streamerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Streamer streamer = streamerOpt.get();
        Boolean active = body.get("isActive");
        if (active != null) {
            streamer.setActive(active);
            if (!active && recorderService.isRecording(streamer.getId())) {
                recorderService.stopRecording(streamer.getId());
            }
            streamerRepository.save(streamer);
        }
        return ResponseEntity.ok(streamer);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchTwitchChannels(@RequestParam String query) {
        return ResponseEntity.ok(twitchClientService.searchChannels(query));
    }
}
