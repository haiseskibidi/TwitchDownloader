package com.twitchdownloader.dto;

import java.util.List;

public record TwitchSearchResponse(
    List<TwitchSearchChannel> data
) {}
