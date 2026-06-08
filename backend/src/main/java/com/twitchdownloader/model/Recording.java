package com.twitchdownloader.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recordings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recording {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "streamer_id", nullable = false)
    private Streamer streamer;

    @Column(name = "twitch_stream_id")
    private String twitchStreamId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingStatus status;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "file_size")
    private Long fileSize;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }
}
