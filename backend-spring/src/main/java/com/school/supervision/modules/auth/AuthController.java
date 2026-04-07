package com.school.supervision.modules.auth;

import com.school.supervision.common.security.JwtService;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password, UUID organizationId) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        User user = resolveLoginUser(request);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token = jwtService.issueToken(user.getUsername(), user.getOrganizationId());
        return Map.of("accessToken", token);
    }

    private User resolveLoginUser(LoginRequest request) {
        if (request.organizationId() != null) {
            return userRepository.findByUsernameAndOrganizationId(request.username(), request.organizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        }
        List<User> matches = userRepository.findAllByUsername(request.username());
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple accounts use this username; specify organizationId in the login request, or contact your administrator.");
        }
        return matches.get(0);
    }
}
