package com.twitchdownloader.dto;

import java.util.List;

public record TwitchUsersResponse(
    List<TwitchUser> data
) {}
