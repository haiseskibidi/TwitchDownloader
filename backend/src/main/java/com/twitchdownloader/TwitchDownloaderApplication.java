package com.twitchdownloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.File;

@SpringBootApplication
@EnableScheduling
public class TwitchDownloaderApplication {
    static {
        // Гарантируем наличие папки для SQLite БД перед инициализацией Hibernate/HikariCP
        File dbDir = new File("db");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(TwitchDownloaderApplication.class, args);
    }
}

