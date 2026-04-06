package com.school.supervision.modules.users;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.supervision.SupervisionStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupervisionStatsService supervisionStatsService;
    private final SchoolRepository schoolRepository;
    private final AssignmentRepository assignmentRepository;

    public UserController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          SupervisionStatsService supervisionStatsService,
                          SchoolRepository schoolRepository,
                          AssignmentRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.supervisionStatsService = supervisionStatsService;
        this.schoolRepository = schoolRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public record CreateUserRequest(@NotBlank String username,
                                    @NotBlank String password,
                                    @NotBlank String fullName,
                                    String email,
                                    String phone,
                                    String city,
                                    String subCity,
                                    String wereda) {}
    public record UserProfileResponse(UUID id,
                                      String username,
                                      String fullName,
                                      String email,
                                      String city,
                                      String subCity,
                                      String wereda,
                                      List<String> roles) {}

    public record UpdateMyProfileRequest(String fullName, String email, String city, String subCity, String wereda) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 200) String newPassword
    ) {}
    public record UpdateManagedUserRequest(
            String fullName,
            String email,
            String phone,
            String city,
            String subCity,
            String wereda
    ) {}

    @PostMapping
    public UUID create(Authentication authentication, @RequestBody CreateUserRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        User user = new User();
        user.setOrganizationId(requireTenant());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setCity(request.city());
        user.setSubCity(request.subCity());
        user.setWereda(request.wereda());
        return userRepository.save(user).getId();
    }

    @GetMapping
    public List<UserProfileResponse> list(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        return userRepository.findAllByOrganizationId(requireTenant()).stream()
                .map(this::toProfile)
                .toList();
    }

    @GetMapping("/me")
    public UserProfileResponse me(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        return toProfile(user);
    }

    @GetMapping("/me/status")
    public SupervisionStatsService.UserStatusPayload myStatus(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        return supervisionStatsService.statusFor(user, requireTenant());
    }

    @PatchMapping(value = "/me", consumes = "application/json")
    public UserProfileResponse updateMyProfile(Authentication authentication,
                                               @RequestBody(required = false) UpdateMyProfileRequest body) {
        User user = requireCurrentUser(authentication);
        if (body != null) {
            if (body.fullName() != null && !body.fullName().isBlank()) {
                user.setFullName(body.fullName().trim());
            }
            if (body.email() != null) {
                String e = body.email().trim();
                user.setEmail(e.isEmpty() ? null : e);
            }
            if (body.city() != null) {
                String c = body.city().trim();
                user.setCity(c.isEmpty() ? null : c);
            }
            if (body.subCity() != null) {
                String s = body.subCity().trim();
                user.setSubCity(s.isEmpty() ? null : s);
            }
            if (body.wereda() != null) {
                String w = body.wereda().trim();
                user.setWereda(w.isEmpty() ? null : w);
            }
            userRepository.save(user);
        }
        return toProfile(user);
    }

    @PostMapping("/me/change-password")
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        User user = requireCurrentUser(authentication);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @GetMapping("/cluster-coordinators")
    public List<UserProfileResponse> clusterCoordinators(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireSuperAdmin(current);
        return userRepository.findAllByOrganizationId(requireTenant()).stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> "CLUSTER_COORDINATOR".equals(r.getName())))
                .map(this::toProfile)
                .toList();
    }

    @GetMapping("/supervisors")
    public List<UserProfileResponse> supervisors(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        List<User> users = isSuperAdmin(current)
                ? userRepository.findAllByOrganizationId(requireTenant())
                : userRepository.findAllByOrganizationIdAndCoordinatorUserId(requireTenant(), current.getId());
        return users.stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName())))
                .map(this::toProfile)
                .toList();
    }

    @PostMapping("/cluster-coordinators")
    public UUID createClusterCoordinator(Authentication authentication, @RequestBody CreateUserRequest request) {
        User current = requireCurrentUser(authentication);
        requireSuperAdmin(current);
        if (request.city() == null || request.city().isBlank()
                || request.subCity() == null || request.subCity().isBlank()
                || request.wereda() == null || request.wereda().isBlank()) {
            throw new IllegalArgumentException("Cluster coordinator requires city, sub city, and wereda");
        }
        Role coordinatorRole = roleRepository.findByOrganizationIdAndName(requireTenant(), "CLUSTER_COORDINATOR")
                .orElseThrow(() -> new IllegalArgumentException("Role CLUSTER_COORDINATOR not found"));
        User user = new User();
        user.setOrganizationId(requireTenant());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setCity(request.city());
        user.setSubCity(request.subCity());
        user.setWereda(request.wereda());
        user.getRoles().add(coordinatorRole);
        return userRepository.save(user).getId();
    }

    @PostMapping("/supervisors")
    public UUID createSupervisor(Authentication authentication, @RequestBody CreateUserRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        Role supervisorRole = roleRepository.findByOrganizationIdAndName(requireTenant(), "SUPERVISOR")
                .orElseThrow(() -> new IllegalArgumentException("Role SUPERVISOR not found"));
        User user = new User();
        user.setOrganizationId(requireTenant());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        if (isSuperAdmin(current)) {
            user.setCity(request.city());
            user.setSubCity(request.subCity());
            user.setWereda(request.wereda());
        } else {
            user.setCity(current.getCity());
            user.setSubCity(current.getSubCity());
            user.setWereda(current.getWereda());
            user.setCoordinatorUserId(current.getId());
        }
        user.getRoles().add(supervisorRole);
        return userRepository.save(user).getId();
    }

    @PatchMapping("/cluster-coordinators/{userId}")
    public UUID updateClusterCoordinator(Authentication authentication,
                                         @PathVariable UUID userId,
                                         @RequestBody UpdateManagedUserRequest request) {
        User current = requireCurrentUser(authentication);
        requireSuperAdmin(current);
        User user = requireManagedUserWithRole(userId, "CLUSTER_COORDINATOR");
        applyManagedUserUpdate(user, request, true);
        userRepository.save(user);
        return user.getId();
    }

    @DeleteMapping("/cluster-coordinators/{userId}")
    public void deleteClusterCoordinator(Authentication authentication, @PathVariable UUID userId) {
        User current = requireCurrentUser(authentication);
        requireSuperAdmin(current);
        UUID orgId = requireTenant();
        User user = requireManagedUserWithRole(userId, "CLUSTER_COORDINATOR");
        if (!schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, userId).isEmpty()) {
            throw new IllegalArgumentException("Cannot delete coordinator that has assigned schools.");
        }
        if (!userRepository.findSupervisorsForCoordinator(orgId, userId).isEmpty()) {
            throw new IllegalArgumentException("Cannot delete coordinator that has assigned supervisors.");
        }
        userRepository.delete(user);
    }

    @PatchMapping("/supervisors/{userId}")
    public UUID updateSupervisor(Authentication authentication,
                                 @PathVariable UUID userId,
                                 @RequestBody UpdateManagedUserRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        User user = requireManagedUserWithRole(userId, "SUPERVISOR");
        if (!isSuperAdmin(current) && !current.getId().equals(user.getCoordinatorUserId())) {
            throw new AccessDeniedException("Supervisor not found in coordinator scope");
        }
        applyManagedUserUpdate(user, request, isSuperAdmin(current));
        userRepository.save(user);
        return user.getId();
    }

    @DeleteMapping("/supervisors/{userId}")
    public void deleteSupervisor(Authentication authentication, @PathVariable UUID userId) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        User user = requireManagedUserWithRole(userId, "SUPERVISOR");
        if (!isSuperAdmin(current) && !current.getId().equals(user.getCoordinatorUserId())) {
            throw new AccessDeniedException("Supervisor not found in coordinator scope");
        }
        if (assignmentRepository.existsByOrganizationIdAndSupervisorId(orgId, userId)) {
            throw new IllegalArgumentException("Cannot delete supervisor that has assignments.");
        }
        userRepository.delete(user);
    }

    @PatchMapping("/{userId}/roles")
    public UUID assignRoles(Authentication authentication, @PathVariable UUID userId, @RequestBody Set<UUID> roleIds) {
        User current = requireCurrentUser(authentication);
        requireSuperAdmin(current);
        UUID orgId = requireTenant();
        User user = userRepository.findByIdAndOrganizationId(userId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<Role> roles = roleIds.stream()
                .map(roleId -> roleRepository.findByIdAndOrganizationId(roleId, orgId)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId)))
                .toList();
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        userRepository.save(user);
        return user.getId();
    }

    @GetMapping("/roles")
    public List<MapRoleResponse> listRoles(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireSuperAdmin(current);
        return roleRepository.findAllByOrganizationId(requireTenant()).stream()
                .map(role -> new MapRoleResponse(role.getId(), role.getName()))
                .collect(Collectors.toList());
    }

    public record MapRoleResponse(UUID id, String name) {}

    private UUID requireTenant() {
        UUID orgId = TenantContext.getOrganizationId();
        if (orgId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return orgId;
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return userRepository.findByUsernameAndOrganizationId(authentication.getName(), requireTenant())
                .orElseThrow(() -> new AccessDeniedException("Current user not found"));
    }

    private void requireSuperAdmin(User user) {
        boolean isSuperAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
        if (!isSuperAdmin) {
            throw new AccessDeniedException("Only SUPER_ADMIN can perform this action");
        }
    }

    private void requireAdminOrCoordinator(User user) {
        boolean allowed = user.getRoles().stream()
                .anyMatch(r -> "SUPER_ADMIN".equals(r.getName()) || "CLUSTER_COORDINATOR".equals(r.getName()));
        if (!allowed) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can perform this action");
        }
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
    }

    private User requireManagedUserWithRole(UUID userId, String roleName) {
        User user = userRepository.findByIdAndOrganizationId(userId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        boolean hasRole = user.getRoles().stream().anyMatch(r -> roleName.equals(r.getName()));
        if (!hasRole) {
            throw new IllegalArgumentException("User is not a " + roleName);
        }
        return user;
    }

    private void applyManagedUserUpdate(User user, UpdateManagedUserRequest request, boolean allowLocationUpdate) {
        if (request == null) return;
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null) {
            String v = request.email().trim();
            user.setEmail(v.isEmpty() ? null : v);
        }
        if (request.phone() != null) {
            String v = request.phone().trim();
            user.setPhone(v.isEmpty() ? null : v);
        }
        if (allowLocationUpdate) {
            if (request.city() != null) {
                String v = request.city().trim();
                user.setCity(v.isEmpty() ? null : v);
            }
            if (request.subCity() != null) {
                String v = request.subCity().trim();
                user.setSubCity(v.isEmpty() ? null : v);
            }
            if (request.wereda() != null) {
                String v = request.wereda().trim();
                user.setWereda(v.isEmpty() ? null : v);
            }
        }
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getCity(),
                user.getSubCity(),
                user.getWereda(),
                user.getRoles().stream().map(Role::getName).toList()
        );
    }
}
