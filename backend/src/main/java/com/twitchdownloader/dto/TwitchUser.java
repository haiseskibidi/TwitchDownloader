package com.twitchdownloader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TwitchUser(
    String id,
    String login,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("profile_image_url") String profileImageUrl
) {}
