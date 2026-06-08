package com.twitchdownloader.service;

import com.twitchdownloader.model.Setting;
import com.twitchdownloader.repository.SettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SettingService {
    private final SettingRepository settingRepository;

    @Value("${app.twitch.client-id}")
    private String defaultClientId;

    @Value("${app.twitch.client-secret}")
    private String defaultClientSecret;

    @Value("${app.download.path}")
    private String defaultDownloadPath;

    @Value("${app.download.quality}")
    private String defaultQuality;

    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public String getSetting(String key, String defaultValue) {
        return settingRepository.findById(key)
                .map(Setting::getValue)
                .orElse(defaultValue);
    }

    public void saveSetting(String key, String value) {
        settingRepository.save(new Setting(key, value));
    }

    public String getTwitchClientId() {
        return getSetting("twitch_client_id", defaultClientId);
    }

    public String getTwitchClientSecret() {
        return getSetting("twitch_client_secret", defaultClientSecret);
    }

    public String getDownloadPath() {
        return getSetting("download_path", defaultDownloadPath);
    }

    public String getDownloadQuality() {
        return getSetting("download_quality", defaultQuality);
    }

    public boolean isDiskCleanupEnabled() {
        return Boolean.parseBoolean(getSetting("disk_cleanup_enabled", "true"));
    }

    public double getFreeSpaceThresholdPercent() {
        return Double.parseDouble(getSetting("free_space_threshold_percent", "10.0"));
    }

    public Map<String, String> getAllSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("twitch_client_id", getTwitchClientId());
        
        String secret = getTwitchClientSecret();
        if (secret != null && !secret.trim().isEmpty()) {
            settings.put("twitch_client_secret", "********************");
        } else {
            settings.put("twitch_client_secret", "");
        }
        
        settings.put("download_path", getDownloadPath());
        settings.put("download_quality", getDownloadQuality());
        settings.put("disk_cleanup_enabled", String.valueOf(isDiskCleanupEnabled()));
        settings.put("free_space_threshold_percent", String.valueOf(getFreeSpaceThresholdPercent()));
        return settings;
    }

    public void saveAllSettings(Map<String, String> settings) {
        settings.forEach((key, value) -> {
            if ("twitch_client_secret".equals(key) && "********************".equals(value)) {
                // Do not overwrite the secret with the masked placeholder
                return;
            }
            saveSetting(key, value);
        });
    }

}
