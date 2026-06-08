package com.twitchdownloader.repository;

import com.twitchdownloader.model.Recording;
import com.twitchdownloader.model.RecordingStatus;
import com.twitchdownloader.model.Streamer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordingRepository extends JpaRepository<Recording, Long> {
    List<Recording> findByStatus(RecordingStatus status);
    List<Recording> findByStreamerOrderByStartedAtDesc(Streamer streamer);
    List<Recording> findByOrderByStartedAtDesc();
    List<Recording> findByStreamerInOrderByStartedAtDesc(List<Streamer> streamers);
}
