package com.twitchdownloader.scheduler;

import com.twitchdownloader.dto.TwitchStream;
import com.twitchdownloader.model.Streamer;
import com.twitchdownloader.repository.StreamerRepository;
import com.twitchdownloader.service.RecorderService;
import com.twitchdownloader.service.TwitchClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TwitchScheduler {
    private static final Logger log = LoggerFactory.getLogger(TwitchScheduler.class);

    private final StreamerRepository streamerRepository;
    private final TwitchClientService twitchClientService;
    private final RecorderService recorderService;

    public TwitchScheduler(StreamerRepository streamerRepository,
                           TwitchClientService twitchClientService,
                           RecorderService recorderService) {
        this.streamerRepository = streamerRepository;
        this.twitchClientService = twitchClientService;
        this.recorderService = recorderService;
    }

    @Scheduled(fixedDelay = 60000) // Runs every 60 seconds
    public void checkStreams() {


        List<Streamer> activeStreamers = streamerRepository.findByIsActiveTrue();
        if (activeStreamers.isEmpty()) {
            return;
        }

        log.debug("Checking Twitch status for {} active streamers...", activeStreamers.size());

        List<String> usernames = activeStreamers.stream()
                .map(Streamer::getTwitchUsername)
                .toList();

        List<TwitchStream> liveStreams = twitchClientService.getStreamsInfo(usernames);

        // Map online stream logins to their stream info
        Map<String, TwitchStream> onlineStreams = liveStreams.stream()
                .collect(Collectors.toMap(
                        stream -> stream.userLogin().toLowerCase(),
                        stream -> stream,
                        (s1, s2) -> s1
                ));

        for (Streamer streamer : activeStreamers) {
            String usernameLower = streamer.getTwitchUsername().toLowerCase();
            boolean currentlyRecording = recorderService.isRecording(streamer.getId());

            if (onlineStreams.containsKey(usernameLower)) {
                TwitchStream streamInfo = onlineStreams.get(usernameLower);
                if (!currentlyRecording) {
                    log.info("Streamer {} went ONLINE. Starting download. Title: {}", streamer.getTwitchUsername(), streamInfo.title());
                    recorderService.startRecording(streamer, streamInfo.id(), streamInfo.title());
                }
            } else {
                if (currentlyRecording) {
                    log.info("Streamer {} went OFFLINE. Streamlink process should exit soon.", streamer.getTwitchUsername());
                }
            }
        }
    }
}
