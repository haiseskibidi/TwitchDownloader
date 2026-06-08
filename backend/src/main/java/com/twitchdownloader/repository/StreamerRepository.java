package com.twitchdownloader.repository;

import com.twitchdownloader.model.Streamer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StreamerRepository extends JpaRepository<Streamer, Long> {
    Optional<Streamer> findByTwitchUsernameIgnoreCase(String twitchUsername);
    List<Streamer> findByIsActiveTrue();
}
