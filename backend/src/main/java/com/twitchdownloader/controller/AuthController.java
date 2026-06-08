package com.twitchdownloader.controller;

import com.twitchdownloader.dto.AuthRequest;
import com.twitchdownloader.model.User;
import com.twitchdownloader.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request, HttpSession session) {
        String username = request.getUsername().trim();
        String password = request.getPassword();

        if (username.length() < 3 || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username must be at least 3 characters and password at least 6 characters"));
        }

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username is already taken"));
        }

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        User user = User.builder()
                .username(username)
                .passwordHash(hashedPassword)
                .role("USER")
                .createdAt(Instant.now())
                .build();

        user = userRepository.save(user);
        log.info("Registered user: {}", username);

        // Auto-login after registration
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpSession session) {
        String username = request.getUsername().trim();
        String password = request.getPassword();

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }

        User user = userOpt.get();
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }

        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        log.info("User logged in: {}", username);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        String username = (String) session.getAttribute("username");
        session.invalidate();
        log.info("User logged out: {}", username);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            session.invalidate();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User session invalid"));
        }

        User user = userOpt.get();
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }

    @GetMapping("/has-users")
    public ResponseEntity<?> hasUsers() {
        boolean exists = userRepository.count() > 0;
        return ResponseEntity.ok(Map.of("hasUsers", exists));
    }
}
