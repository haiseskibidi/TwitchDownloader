package com.twitchdownloader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TwitchSearchChannel(
    String id,
    @JsonProperty("broadcaster_login") String broadcasterLogin,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("thumbnail_url") String thumbnailUrl,
    @JsonProperty("is_live") boolean isLive
) {}
