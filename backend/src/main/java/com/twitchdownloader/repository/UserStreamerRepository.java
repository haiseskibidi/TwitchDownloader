package com.twitchdownloader.repository;

import com.twitchdownloader.model.Streamer;
import com.twitchdownloader.model.User;
import com.twitchdownloader.model.UserStreamer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStreamerRepository extends JpaRepository<UserStreamer, Long> {
    List<UserStreamer> findByUser(User user);
    List<UserStreamer> findByUserId(Long userId);
    Optional<UserStreamer> findByUserIdAndStreamerId(Long userId, Long streamerId);
    Optional<UserStreamer> findByUserAndStreamer(User user, Streamer streamer);
    List<UserStreamer> findByStreamer(Streamer streamer);
    boolean existsByStreamer(Streamer streamer);

    @Query("SELECT DISTINCT us.streamer FROM UserStreamer us WHERE us.isActive = true")
    List<Streamer> findActiveStreamers();
    
    @Query("SELECT us.streamer FROM UserStreamer us WHERE us.user.id = :userId")
    List<Streamer> findStreamersByUserId(Long userId);
}
