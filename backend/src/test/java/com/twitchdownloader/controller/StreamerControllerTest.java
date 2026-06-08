package com.twitchdownloader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twitchdownloader.dto.AuthRequest;
import com.twitchdownloader.model.Streamer;
import com.twitchdownloader.model.User;
import com.twitchdownloader.model.UserStreamer;
import com.twitchdownloader.repository.StreamerRepository;
import com.twitchdownloader.repository.UserRepository;
import com.twitchdownloader.repository.UserStreamerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StreamerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StreamerRepository streamerRepository;

    @Autowired
    private UserStreamerRepository userStreamerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;

    @BeforeEach
    public void setup() throws Exception {
        userStreamerRepository.deleteAll();
        streamerRepository.deleteAll();
        userRepository.deleteAll();

        // Register User A
        AuthRequest reqA = new AuthRequest("userA", "password123");
        sessionA = new MockHttpSession();
        MvcResult resA = mockMvc.perform(post("/api/auth/register")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk())
                .andReturn();
        Long idA = ((Number) objectMapper.readValue(resA.getResponse().getContentAsString(), Map.class).get("id")).longValue();
        userA = userRepository.findById(idA).orElseThrow();

        // Register User B
        AuthRequest reqB = new AuthRequest("userB", "password123");
        sessionB = new MockHttpSession();
        MvcResult resB = mockMvc.perform(post("/api/auth/register")
                .session(sessionB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk())
                .andReturn();
        Long idB = ((Number) objectMapper.readValue(resB.getResponse().getContentAsString(), Map.class).get("id")).longValue();
        userB = userRepository.findById(idB).orElseThrow();
    }

    @Test
    public void testUserSpecificStreamerFlow() throws Exception {
        // 1. User A adds a streamer
        Map<String, String> addBody = Map.of(
                "username", "streamerX",
                "displayName", "Streamer X",
                "twitchId", "12345",
                "profileImageUrl", "http://image.url"
        );

        mockMvc.perform(post("/api/streamers")
                .session(sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twitchUsername").value("streamerx"));

        // 2. Verify User A sees 1 streamer
        mockMvc.perform(get("/api/streamers").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].twitchUsername").value("streamerx"));

        // 3. Verify User B sees 0 streamers
        mockMvc.perform(get("/api/streamers").session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 4. User B adds the SAME streamer (should link to existing global streamer)
        mockMvc.perform(post("/api/streamers")
                .session(sessionB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addBody)))
                .andExpect(status().isOk());

        // Now both see the streamer
        mockMvc.perform(get("/api/streamers").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/streamers").session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Verify only 1 global Streamer record is created
        assertEquals(1, streamerRepository.count());
        // But 2 links exist in the join table
        assertEquals(2, userStreamerRepository.count());

        // 5. User A deletes the streamer
        Streamer streamer = streamerRepository.findByTwitchUsernameIgnoreCase("streamerx").orElseThrow();
        mockMvc.perform(delete("/api/streamers/" + streamer.getId()).session(sessionA))
                .andExpect(status().isOk());

        // User A now sees 0
        mockMvc.perform(get("/api/streamers").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // User B still sees 1
        mockMvc.perform(get("/api/streamers").session(sessionB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Global streamer record is not deleted
        assertEquals(1, streamerRepository.count());
    }
}
