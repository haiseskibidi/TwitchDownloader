package com.twitchdownloader.service;

import com.twitchdownloader.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TwitchClientService {
    private static final Logger log = LoggerFactory.getLogger(TwitchClientService.class);

    private final SettingService settingService;
    private final RestClient restClient;

    private String cachedAccessToken;
    private Instant tokenExpirationTime;

    public TwitchClientService(SettingService settingService) {
        this.settingService = settingService;
        this.restClient = RestClient.builder().build();
    }

    public synchronized boolean isConfigured() {
        String clientId = settingService.getTwitchClientId();
        String clientSecret = settingService.getTwitchClientSecret();
        return clientId != null && !clientId.trim().isEmpty() &&
               clientSecret != null && !clientSecret.trim().isEmpty();
    }

    private synchronized String getAccessToken() {
        if (!isConfigured()) {
            throw new IllegalStateException("Twitch Client ID and Client Secret must be configured in Settings!");
        }

        if (cachedAccessToken != null && tokenExpirationTime != null && Instant.now().isBefore(tokenExpirationTime)) {
            return cachedAccessToken;
        }

        String clientId = settingService.getTwitchClientId();
        String clientSecret = settingService.getTwitchClientSecret();

        log.info("Requesting new Twitch Access Token...");
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("grant_type", "client_credentials");

            TwitchTokenResponse response = restClient.post()
                    .uri("https://id.twitch.tv/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(TwitchTokenResponse.class);

            if (response != null && response.accessToken() != null) {
                cachedAccessToken = response.accessToken();
                tokenExpirationTime = Instant.now().plusSeconds(response.expiresIn() - 60);
                log.info("Twitch Access Token successfully acquired.");
                return cachedAccessToken;
            }
        } catch (Exception e) {
            log.error("Failed to obtain Twitch access token: {}", e.getMessage());
            cachedAccessToken = null;
            tokenExpirationTime = null;
        }
        return null;
    }

    private JsonNode postGql(List<Map<String, Object>> payload) {
        try {
            return restClient.post()
                    .uri("https://gql.twitch.tv/gql")
                    .header("Client-ID", "kimne78kx3ncx6brgo4mv6wki5h1ko")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Failed to call Twitch GQL API: {}", e.getMessage());
            return null;
        }
    }

    private Optional<TwitchUser> getUserInfoGql(String username) {
        log.info("Fetching Twitch user info using public GQL fallback for: {}", username);
        String query = "query GetUserInfo($login: String!) { user(login: $login) { id displayName profileImageURL(width: 300) } }";
        Map<String, Object> variables = Map.of("login", username);
        List<Map<String, Object>> payload = List.of(Map.of(
                "operationName", "GetUserInfo",
                "variables", variables,
                "query", query
        ));
        
        JsonNode response = postGql(payload);
        if (response != null && response.isArray() && response.size() > 0) {
            JsonNode userNode = response.get(0).path("data").path("user");
            if (!userNode.isMissingNode() && !userNode.isNull()) {
                String id = userNode.path("id").asText();
                String displayName = userNode.path("displayName").asText();
                String profileImageUrl = userNode.path("profileImageURL").asText();
                return Optional.of(new TwitchUser(id, username, displayName, profileImageUrl));
            }
        }
        return Optional.empty();
    }

    private List<TwitchStream> getStreamsInfoGql(List<String> usernames) {
        log.info("Checking stream status using public GQL fallback for {} streamers...", usernames.size());
        List<Map<String, Object>> payload = new java.util.ArrayList<>();
        String query = "query GetUserInfo($login: String!) { user(login: $login) { id displayName profileImageURL(width: 300) stream { id title type viewersCount createdAt } } }";
        
        for (String username : usernames) {
            payload.add(Map.of(
                    "operationName", "GetUserInfo",
                    "variables", Map.of("login", username),
                    "query", query
            ));
        }
        
        JsonNode response = postGql(payload);
        List<TwitchStream> liveStreams = new java.util.ArrayList<>();
        if (response != null && response.isArray()) {
            for (int i = 0; i < response.size(); i++) {
                JsonNode dataNode = response.get(i).path("data");
                JsonNode userNode = dataNode.path("user");
                if (userNode != null && !userNode.isMissingNode() && !userNode.isNull()) {
                    String displayName = userNode.path("displayName").asText();
                    String login = usernames.get(i).toLowerCase();
                    JsonNode streamNode = userNode.path("stream");
                    if (streamNode != null && !streamNode.isMissingNode() && !streamNode.isNull()) {
                        String streamId = streamNode.path("id").asText();
                        String title = streamNode.path("title").asText();
                        String type = streamNode.path("type").asText();
                        String createdAt = streamNode.path("createdAt").asText();
                        liveStreams.add(new TwitchStream(streamId, login, displayName, title, type, createdAt));
                    }
                }
            }
        }
        return liveStreams;
    }

    private List<TwitchSearchChannel> searchChannelsGql(String searchPattern) {
        log.info("Searching Twitch channels using public GQL fallback for query: {}", searchPattern);
        
        // 1. Get suggestions
        String suggestQuery = "query SearchSuggestions($query: String!) { searchSuggestions(queryFragment: $query) { edges { node { text } } } }";
        List<Map<String, Object>> suggestPayload = List.of(Map.of(
                "operationName", "SearchSuggestions",
                "variables", Map.of("query", searchPattern),
                "query", suggestQuery
        ));
        
        JsonNode suggestRes = postGql(suggestPayload);
        List<String> usernames = new java.util.ArrayList<>();
        if (suggestRes != null && suggestRes.isArray() && suggestRes.size() > 0) {
            JsonNode edgesNode = suggestRes.get(0).path("data").path("searchSuggestions").path("edges");
            if (edgesNode.isArray()) {
                for (JsonNode edge : edgesNode) {
                    String username = edge.path("node").path("text").asText();
                    if (username != null && !username.trim().isEmpty()) {
                        usernames.add(username);
                    }
                }
            }
        }
        
        // If the query itself is not in the suggestions, add it to check for exact match
        String queryClean = searchPattern.trim().toLowerCase();
        if (!usernames.contains(queryClean)) {
            usernames.add(0, queryClean);
        }
        
        if (usernames.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Limit to top 5 results to keep it fast
        List<String> topUsernames = usernames.stream().limit(5).toList();
        
        // 2. Fetch full user info and stream status for these users in batch
        List<Map<String, Object>> infoPayload = new java.util.ArrayList<>();
        String infoQuery = "query GetUserInfo($login: String!) { user(login: $login) { id displayName profileImageURL(width: 150) stream { id type } } }";
        for (String user : topUsernames) {
            infoPayload.add(Map.of(
                    "operationName", "GetUserInfo",
                    "variables", Map.of("login", user),
                    "query", infoQuery
            ));
        }
        
        JsonNode infoRes = postGql(infoPayload);
        List<TwitchSearchChannel> results = new java.util.ArrayList<>();
        if (infoRes != null && infoRes.isArray()) {
            for (int i = 0; i < infoRes.size(); i++) {
                JsonNode userNode = infoRes.get(i).path("data").path("user");
                if (userNode != null && !userNode.isMissingNode() && !userNode.isNull()) {
                    String id = userNode.path("id").asText();
                    String displayName = userNode.path("displayName").asText();
                    String profileImageUrl = userNode.path("profileImageURL").asText();
                    boolean isLive = userNode.path("stream") != null && 
                                     !userNode.path("stream").isMissingNode() && 
                                     !userNode.path("stream").isNull();
                    String login = topUsernames.get(i);
                    results.add(new TwitchSearchChannel(id, login, displayName, profileImageUrl, isLive));
                }
            }
        }
        
        return results;
    }

    public Optional<TwitchUser> getUserInfo(String username) {
        if (!isConfigured()) return getUserInfoGql(username);

        String token = getAccessToken();
        if (token == null) {
            log.warn("Twitch Helix API client failed to authenticate. Falling back to GQL.");
            return getUserInfoGql(username);
        }

        try {
            TwitchUsersResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.twitch.tv")
                            .path("/helix/users")
                            .queryParam("login", username)
                            .build())
                    .header("Client-ID", settingService.getTwitchClientId())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(TwitchUsersResponse.class);

            if (response != null && response.data() != null && !response.data().isEmpty()) {
                return Optional.of(response.data().get(0));
            }
        } catch (Exception e) {
            log.error("Helix error fetching user info from Twitch for {}, falling back to GQL: {}", username, e.getMessage());
            return getUserInfoGql(username);
        }
        return Optional.empty();
    }

    public List<TwitchStream> getStreamsInfo(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyList();
        }

        if (!isConfigured()) return getStreamsInfoGql(usernames);

        String token = getAccessToken();
        if (token == null) {
            log.warn("Twitch Helix API client failed to authenticate. Falling back to GQL.");
            return getStreamsInfoGql(usernames);
        }

        try {
            TwitchStreamsResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.scheme("https").host("api.twitch.tv").path("/helix/streams");
                        for (String username : usernames) {
                            uriBuilder.queryParam("user_login", username);
                        }
                        return uriBuilder.build();
                    })
                    .header("Client-ID", settingService.getTwitchClientId())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(TwitchStreamsResponse.class);

            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.error("Helix error fetching streams info, falling back to GQL: {}", e.getMessage());
            return getStreamsInfoGql(usernames);
        }
        return Collections.emptyList();
    }

    public List<TwitchSearchChannel> searchChannels(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (!isConfigured()) return searchChannelsGql(query);

        String token = getAccessToken();
        if (token == null) {
            log.warn("Twitch Helix API client failed to authenticate. Falling back to GQL.");
            return searchChannelsGql(query);
        }

        try {
            TwitchSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.twitch.tv")
                            .path("/helix/search/channels")
                            .queryParam("query", query)
                            .queryParam("first", "5")
                            .build())
                    .header("Client-ID", settingService.getTwitchClientId())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(TwitchSearchResponse.class);

            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.error("Helix error searching channels, falling back to GQL: {}", e.getMessage());
            return searchChannelsGql(query);
        }
        return Collections.emptyList();
    }
}

