package com.twitchdownloader.controller;

import com.twitchdownloader.service.DiskManagerService;
import com.twitchdownloader.service.SettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SystemController {
    private final DiskManagerService diskManagerService;
    private final SettingService settingService;

    public SystemController(DiskManagerService diskManagerService, SettingService settingService) {
        this.diskManagerService = diskManagerService;
        this.settingService = settingService;
    }

    @GetMapping("/system/stats")
    public Map<String, Object> getSystemStats() {
        return diskManagerService.getDiskStats();
    }

    @GetMapping("/settings")
    public Map<String, String> getSettings() {
        return settingService.getAllSettings();
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody Map<String, String> settings) {
        settingService.saveAllSettings(settings);
        return ResponseEntity.ok(Map.of("message", "Settings updated successfully"));
    }
}
