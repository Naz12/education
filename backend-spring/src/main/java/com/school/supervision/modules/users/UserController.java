package com.school.supervision.modules.users;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.importexport.ExcelWorkbookService;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.supervision.SupervisionStatsService;
import com.school.supervision.modules.organization.GeographyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final ObjectMapper objectMapper;
    private final GeographyService geographyService;
    private final ExcelWorkbookService excelWorkbookService;

    public UserController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          SupervisionStatsService supervisionStatsService,
                          SchoolRepository schoolRepository,
                          AssignmentRepository assignmentRepository,
                          ObjectMapper objectMapper,
                          GeographyService geographyService,
                          ExcelWorkbookService excelWorkbookService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.supervisionStatsService = supervisionStatsService;
        this.schoolRepository = schoolRepository;
        this.assignmentRepository = assignmentRepository;
        this.objectMapper = objectMapper;
        this.geographyService = geographyService;
        this.excelWorkbookService = excelWorkbookService;
    }

    public record CreateUserRequest(@NotBlank String username,
                                    @NotBlank String password,
                                    @NotBlank String fullName,
                                    String email,
                                    String phone,
                                    String city,
                                    String subCity,
                                    String wereda,
                                    List<String> supervisedGradeCodes,
                                    java.util.UUID weredaId) {}
    public record UserProfileResponse(UUID id,
                                      String username,
                                      String fullName,
                                      String email,
                                      String city,
                                      String subCity,
                                      String wereda,
                                      UUID cityId,
                                      UUID subcityId,
                                      UUID weredaId,
                                      List<String> roles,
                                      List<String> supervisedGradeCodes) {}

    public record CreateClusterCoordinatorRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String fullName,
            String email,
            String phone,
            @NotNull UUID weredaId
    ) {}

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
            String wereda,
            List<String> supervisedGradeCodes,
            UUID weredaId
    ) {}
    public record BulkImportResult(int created, int skipped, int failed, List<Map<String, String>> errors) {}

    @PostMapping
    public UUID create(Authentication authentication, @RequestBody CreateUserRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        String username = request.username().trim();
        if (userRepository.existsByUsernameAndOrganizationId(username, orgId)) {
            throw new IllegalArgumentException("Username already exists in this organization.");
        }
        User user = new User();
        user.setOrganizationId(orgId);
        user.setUsername(username);
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

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        List<String> headers = List.of(
                "roleType", "fullName", "username", "password", "email", "phone",
                "city", "subCity", "wereda", "supervisedGradeCodes"
        );
        List<List<String>> sample = List.of(
                List.of("SUPERVISOR", "Sample Supervisor", "supervisor.sample", "12345678", "sup@example.com", "0911000000",
                        "Addis Ababa", "Lideta", "01", "KG1,KG2,1"),
                List.of("CLUSTER_COORDINATOR", "Sample Coordinator", "coordinator.sample", "12345678", "coord@example.com", "0911000001",
                        "", "", "", "")
        );
        List<String> notes = List.of(
                "roleType supported values: SUPERVISOR, CLUSTER_COORDINATOR.",
                "SUPERVISOR requires supervisedGradeCodes (comma-separated canonical grades).",
                "For coordinator users, supervisedGradeCodes must be blank.",
                "When created by CLUSTER_COORDINATOR, supervisor location inherits coordinator scope."
        );
        byte[] bytes = excelWorkbookService.buildTemplate("users", headers, sample, notes);
        return xlsxAttachment("users-template.xlsx", bytes);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication) {
        List<UserProfileResponse> users = list(authentication);
        List<String> headers = List.of(
                "id", "roleType", "fullName", "username", "email", "phone",
                "city", "subCity", "wereda", "supervisedGradeCodes"
        );
        List<List<String>> rows = users.stream()
                .filter(u -> u.roles() != null && (u.roles().contains("SUPERVISOR") || u.roles().contains("CLUSTER_COORDINATOR")))
                .map(u -> List.of(
                        u.id().toString(),
                        u.roles().contains("SUPERVISOR") ? "SUPERVISOR" : "CLUSTER_COORDINATOR",
                        nullToEmpty(u.fullName()),
                        nullToEmpty(u.username()),
                        nullToEmpty(u.email()),
                        "",
                        nullToEmpty(u.city()),
                        nullToEmpty(u.subCity()),
                        nullToEmpty(u.wereda()),
                        String.join(",", u.supervisedGradeCodes() == null ? List.of() : u.supervisedGradeCodes())
                ))
                .toList();
        byte[] bytes = excelWorkbookService.buildExport("users", headers, rows);
        return xlsxAttachment("users-export-" + LocalDate.now() + ".xlsx", bytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkImportResult bulkImport(Authentication authentication, @RequestParam("file") MultipartFile file) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        List<Map<String, String>> rows = excelWorkbookService.parseRows(file);
        int created = 0;
        int skipped = 0;
        List<Map<String, String>> errors = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String rowNum = row.getOrDefault("__rowNum", "?");
            try {
                String roleType = required(row, "roleType").toUpperCase();
                String username = required(row, "username").trim();
                if (userRepository.existsByUsernameAndOrganizationId(username, orgId)) {
                    skipped++;
                    continue;
                }
                if ("SUPERVISOR".equals(roleType)) {
                    CreateUserRequest req = new CreateUserRequest(
                            username,
                            required(row, "password"),
                            required(row, "fullName"),
                            nullable(row.get("email")),
                            nullable(row.get("phone")),
                            nullable(row.get("city")),
                            nullable(row.get("subCity")),
                            nullable(row.get("wereda")),
                            splitCsv(row.get("supervisedGradeCodes")),
                            null
                    );
                    createSupervisor(authentication, req);
                } else if ("CLUSTER_COORDINATOR".equals(roleType)) {
                    User caller = requireCurrentUser(authentication);
                    requireSuperAdmin(caller);
                    throw new IllegalArgumentException("Cluster coordinator bulk import requires weredaId and is not supported in this template yet");
                } else {
                    throw new IllegalArgumentException("Unsupported roleType: " + roleType);
                }
                created++;
            } catch (Exception e) {
                errors.add(Map.of(
                        "row", rowNum,
                        "field", inferFieldFromMessage(e.getMessage()),
                        "message", safeMessage(e)
                ));
            }
        }
        return new BulkImportResult(created, skipped, errors.size(), errors);
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
    public UUID createClusterCoordinator(Authentication authentication,
                                         @Valid @RequestBody CreateClusterCoordinatorRequest request) {
        User current = requireCurrentUser(authentication);
        requireSuperAdmin(current);
        UUID orgId = requireTenant();
        String coordUsername = request.username().trim();
        if (userRepository.existsByUsernameAndOrganizationId(coordUsername, orgId)) {
            throw new IllegalArgumentException("Username already exists in this organization.");
        }
        Role coordinatorRole = roleRepository.findByOrganizationIdAndName(orgId, "CLUSTER_COORDINATOR")
                .orElseThrow(() -> new IllegalArgumentException("Role CLUSTER_COORDINATOR not found"));
        User user = new User();
        user.setOrganizationId(orgId);
        user.setUsername(coordUsername);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        geographyService.applyWeredaToUser(user, request.weredaId());
        user.getRoles().add(coordinatorRole);
        return userRepository.save(user).getId();
    }

    @PostMapping("/supervisors")
    public UUID createSupervisor(Authentication authentication, @RequestBody CreateUserRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        String supUsername = request.username().trim();
        if (userRepository.existsByUsernameAndOrganizationId(supUsername, orgId)) {
            throw new IllegalArgumentException("Username already exists in this organization.");
        }
        Role supervisorRole = roleRepository.findByOrganizationIdAndName(orgId, "SUPERVISOR")
                .orElseThrow(() -> new IllegalArgumentException("Role SUPERVISOR not found"));
        User user = new User();
        user.setOrganizationId(orgId);
        user.setUsername(supUsername);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        if (isSuperAdmin(current)) {
            if (request.weredaId() != null) {
                geographyService.applyWeredaToUser(user, request.weredaId());
            } else if (request.city() != null && !request.city().isBlank()
                    && request.subCity() != null && !request.subCity().isBlank()
                    && request.wereda() != null && !request.wereda().isBlank()) {
                user.setCity(request.city());
                user.setSubCity(request.subCity());
                user.setWereda(request.wereda());
            } else {
                throw new IllegalArgumentException("Super admin must select a wereda or provide city, sub city, and wereda");
            }
        } else {
            user.setCity(current.getCity());
            user.setSubCity(current.getSubCity());
            user.setWereda(current.getWereda());
            user.setCityId(current.getCityId());
            user.setSubcityId(current.getSubcityId());
            user.setWeredaId(current.getWeredaId());
            user.setCoordinatorUserId(current.getId());
        }
        if (request.supervisedGradeCodes() == null || request.supervisedGradeCodes().isEmpty()) {
            throw new IllegalArgumentException("Select at least one grade the supervisor can supervise");
        }
        Set<String> gradeNorm = GradeCodes.normalize(request.supervisedGradeCodes());
        if (gradeNorm.isEmpty()) {
            throw new IllegalArgumentException("No valid grade codes in supervisedGradeCodes");
        }
        try {
            user.setSupervisedGradeCodesJson(objectMapper.writeValueAsString(GradeCodes.sortForDisplay(gradeNorm)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not store supervised grades");
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
            if (request.weredaId() != null) {
                geographyService.applyWeredaToUser(user, request.weredaId());
            } else {
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
        if (request.supervisedGradeCodes() != null) {
            boolean isSupervisor = user.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName()));
            if (isSupervisor) {
                if (request.supervisedGradeCodes().isEmpty()) {
                    throw new IllegalArgumentException("Select at least one grade the supervisor can supervise");
                }
                Set<String> gradeNorm = GradeCodes.normalize(request.supervisedGradeCodes());
                if (gradeNorm.isEmpty()) {
                    throw new IllegalArgumentException("No valid grade codes in supervisedGradeCodes");
                }
                try {
                    user.setSupervisedGradeCodesJson(objectMapper.writeValueAsString(GradeCodes.sortForDisplay(gradeNorm)));
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Could not store supervised grades");
                }
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
                user.getCityId(),
                user.getSubcityId(),
                user.getWeredaId(),
                user.getRoles().stream().map(Role::getName).toList(),
                supervisorGradesForApi(user)
        );
    }

    /** Explicit supervised grades, or null when unset (treated as all grades for legacy supervisors). */
    private List<String> supervisorGradesForApi(User user) {
        boolean isSupervisor = user.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName()));
        if (!isSupervisor) {
            return null;
        }
        Set<String> n = GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, user.getSupervisedGradeCodesJson()));
        if (n.isEmpty()) {
            return null;
        }
        return GradeCodes.sortForDisplay(n);
    }

    private ResponseEntity<byte[]> xlsxAttachment(String filename, byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

    private String required(Map<String, String> row, String field) {
        String value = row.getOrDefault(field, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String nullable(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String inferFieldFromMessage(String msg) {
        if (msg == null) return "row";
        String m = msg.toLowerCase();
        if (m.contains("role")) return "roleType";
        if (m.contains("username")) return "username";
        if (m.contains("password")) return "password";
        if (m.contains("full")) return "fullName";
        if (m.contains("grade")) return "supervisedGradeCodes";
        return "row";
    }

    private String safeMessage(Exception e) {
        return (e.getMessage() == null || e.getMessage().isBlank()) ? "Invalid row data" : e.getMessage();
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
