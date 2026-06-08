package com.twitchdownloader.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "streamers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Streamer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "twitch_username", unique = true, nullable = false)
    private String twitchUsername;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "twitch_id")
    private String twitchId;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;


    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}
