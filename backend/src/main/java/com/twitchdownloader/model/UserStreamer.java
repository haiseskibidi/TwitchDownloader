package com.twitchdownloader.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_streamers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "streamer_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStreamer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "streamer_id", nullable = false)
    private Streamer streamer;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;
}
