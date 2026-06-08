package com.twitchdownloader.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Setting {
    @Id
    @Column(name = "cfg_key", unique = true, nullable = false)
    private String key;

    @Column(name = "cfg_value", length = 1000)
    private String value;
}
