package com.twitchdownloader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TwitchStream(
    String id,
    @JsonProperty("user_login") String userLogin,
    @JsonProperty("user_name") String userName,
    String title,
    String type,
    @JsonProperty("started_at") String startedAt
) {}
