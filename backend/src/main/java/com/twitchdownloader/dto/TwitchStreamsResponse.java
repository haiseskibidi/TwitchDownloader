package com.twitchdownloader.dto;

import java.util.List;

public record TwitchStreamsResponse(
    List<TwitchStream> data
) {}
