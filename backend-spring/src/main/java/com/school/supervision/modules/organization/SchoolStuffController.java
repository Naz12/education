package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.checklists.GradeGroup;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.users.Role;
import com.school.supervision.modules.users.RoleRepository;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/school-stuff")
public class SchoolStuffController {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SchoolStuffController(
            RoleRepository roleRepository,
            UserRepository userRepository,
            TeacherRepository teacherRepository,
            SchoolRepository schoolRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public record SchoolStuffTypeSummary(UUID id, String name, String description) {}

    public record SchoolStuffSummary(
            UUID id,
            String type,
            String fullName,
            String username,
            String email,
            String phone,
            String subject,
            UUID schoolId,
            String schoolName,
            String city,
            String subCity,
            String wereda
    ) {}

    public record CreateSchoolStuffTypeRequest(
            @NotBlank String name,
            @NotBlank String description
    ) {}

    public record CreateSchoolStuffRequest(
            @NotNull UUID roleId,
            @NotBlank String fullName,
            String username,
            String password,
            String email,
            String phone,
            UUID schoolId,
            String subject,
            String city,
            String subCity,
            String wereda
    ) {}

    @GetMapping("/types")
    public List<SchoolStuffTypeSummary> listTypes(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        // School staff "types" are role records that represent non-admin people.
        // We intentionally exclude core system roles from appearing as "staff types".
        Set<String> excluded = Set.of("SUPER_ADMIN", "CLUSTER_COORDINATOR", "SUPERVISOR");

        return roleRepository.findAllByOrganizationId(orgId).stream()
                .filter(r -> !excluded.contains(r.getName()))
                .sorted(Comparator.comparing(Role::getName))
                .map(r -> new SchoolStuffTypeSummary(r.getId(), r.getName(), r.getDescription()))
                .toList();
    }

    @PostMapping("/types")
    public UUID createType(Authentication authentication, @Valid @RequestBody CreateSchoolStuffTypeRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        Set<String> blocked = Set.of("SUPER_ADMIN", "CLUSTER_COORDINATOR", "SUPERVISOR");
        if (blocked.contains(request.name())) {
            throw new IllegalArgumentException("Cannot create reserved role types");
        }

        if (roleRepository.findByOrganizationIdAndName(orgId, request.name()).isPresent()) {
            throw new IllegalArgumentException("Role type already exists");
        }

        Role role = new Role();
        role.setOrganizationId(orgId);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setSystemRole(false);

        UUID id = roleRepository.save(role).getId();
        auditService.record(
                orgId,
                current.getId(),
                "SCHOOL_STUFF_TYPE_CREATED",
                "ROLE",
                id,
                java.util.Map.of("roleName", request.name())
        );
        return id;
    }

    @GetMapping
    public List<SchoolStuffSummary> list(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        boolean isSuperAdmin = isSuperAdmin(current);

        // Prepare schools scope for coordinator filtering and for director mapping.
        List<School> schoolsInScope = isSuperAdmin
                ? schoolRepository.findAllByOrganizationId(orgId)
                : schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, current.getId());
        Map<UUID, School> schoolById = new HashMap<>();
        for (School s : schoolsInScope) schoolById.put(s.getId(), s);

        List<SchoolStuffSummary> result = new ArrayList<>();

        // 1) Teachers (TEACHER role) from teacher table.
        List<Teacher> teachersInScope;
        if (isSuperAdmin) {
            teachersInScope = teacherRepository.findAllByOrganizationId(orgId);
        } else {
            Set<UUID> schoolIds = new HashSet<>();
            for (School s : schoolsInScope) schoolIds.add(s.getId());
            teachersInScope = schoolIds.isEmpty()
                    ? List.of()
                    : teacherRepository.findAllByOrganizationIdAndSchoolIdIn(orgId, schoolIds);
        }

        for (Teacher t : teachersInScope) {
            User u = null;
            if (t.getUserId() != null) {
                u = userRepository.findByIdAndOrganizationId(t.getUserId(), orgId).orElse(null);
            }
            School s = schoolById.get(t.getSchoolId());
            result.add(new SchoolStuffSummary(
                    t.getId(),
                    "TEACHER",
                    t.getName(),
                    u != null ? u.getUsername() : null,
                    u != null ? u.getEmail() : null,
                    u != null ? u.getPhone() : null,
                    t.getSubject(),
                    t.getSchoolId(),
                    s != null ? s.getName() : null,
                    u != null ? u.getCity() : null,
                    u != null ? u.getSubCity() : null,
                    u != null ? u.getWereda() : null
            ));
        }

        // 2) Directors (SCHOOL_DIRECTOR role) mapped from school.director_user_id.
        Set<UUID> directorUserIds = new HashSet<>();
        Map<UUID, School> directorSchoolByUserId = new HashMap<>();
        for (School s : schoolsInScope) {
            if (s.getDirectorUserId() != null) {
                directorUserIds.add(s.getDirectorUserId());
                directorSchoolByUserId.put(s.getDirectorUserId(), s);
            }
        }

        for (UUID directorUserId : directorUserIds) {
            User director = userRepository.findByIdAndOrganizationId(directorUserId, orgId).orElse(null);
            if (director == null) continue;
            boolean hasDirectorRole = director.getRoles().stream().anyMatch(r -> "SCHOOL_DIRECTOR".equals(r.getName()));
            if (!hasDirectorRole) continue;

            School s = directorSchoolByUserId.get(directorUserId);
            result.add(new SchoolStuffSummary(
                    director.getId(),
                    "SCHOOL_DIRECTOR",
                    director.getFullName(),
                    director.getUsername(),
                    director.getEmail(),
                    director.getPhone(),
                    null,
                    s != null ? s.getId() : null,
                    s != null ? s.getName() : null,
                    director.getCity(),
                    director.getSubCity(),
                    director.getWereda()
            ));
        }

        // 3) Other custom role-based staff.
        Set<String> excluded = Set.of("SUPER_ADMIN", "CLUSTER_COORDINATOR", "SUPERVISOR", "TEACHER", "SCHOOL_DIRECTOR");
        Set<String> allowedRoleNames = roleRepository.findAllByOrganizationId(orgId).stream()
                .map(Role::getName)
                .filter(n -> !excluded.contains(n))
                .collect(HashSet::new, HashSet::add, HashSet::addAll);

        for (User u : userRepository.findAllByOrganizationId(orgId)) {
            // Skip admin + excluded roles and users that are already represented (teacher/director).
            boolean hasAllowedRole = u.getRoles().stream().anyMatch(r -> allowedRoleNames.contains(r.getName()));
            if (!hasAllowedRole) continue;
            if (!isSuperAdmin(current)) {
                // Coordinators scope: match the coordinator location fields.
                if (!Objects.equals(u.getCity(), current.getCity())
                        || !Objects.equals(u.getSubCity(), current.getSubCity())
                        || !Objects.equals(u.getWereda(), current.getWereda())) {
                    continue;
                }
            }
            String type = u.getRoles().stream().map(Role::getName).filter(allowedRoleNames::contains).findFirst().orElse("STAFF");
            result.add(new SchoolStuffSummary(
                    u.getId(),
                    type,
                    u.getFullName(),
                    u.getUsername(),
                    u.getEmail(),
                    u.getPhone(),
                    null,
                    null,
                    null,
                    u.getCity(),
                    u.getSubCity(),
                    u.getWereda()
            ));
        }

        // Stable order: type then name.
        result.sort(Comparator.comparing(SchoolStuffSummary::type).thenComparing(SchoolStuffSummary::fullName, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    @PostMapping
    public UUID create(Authentication authentication, @Valid @RequestBody CreateSchoolStuffRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        Role role = roleRepository.findByIdAndOrganizationId(request.roleId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Role type not found"));
        String roleName = role.getName();
        boolean isSuperAdmin = isSuperAdmin(current);

        // Coordinator-scoped location inheritance.
        String city = isSuperAdmin ? request.city() : current.getCity();
        String subCity = isSuperAdmin ? request.subCity() : current.getSubCity();
        String wereda = isSuperAdmin ? request.wereda() : current.getWereda();

        if ("TEACHER".equals(roleName)) {
            if (request.schoolId() == null) throw new IllegalArgumentException("schoolId is required for TEACHER");
            if (request.subject() == null || request.subject().isBlank()) throw new IllegalArgumentException("subject is required for TEACHER");
            if (!isSuperAdmin) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
            }

            UUID userId = null;
            boolean hasLogin = request.username() != null && !request.username().isBlank();
            boolean hasPassword = request.password() != null && !request.password().isBlank();
            if (hasLogin || hasPassword) {
                if (!hasLogin || !hasPassword) {
                    throw new IllegalArgumentException("username and password are required together for TEACHER login");
                }
                User user = new User();
                user.setOrganizationId(orgId);
                user.setUsername(request.username());
                user.setPasswordHash(passwordEncoder.encode(request.password()));
                user.setFullName(request.fullName());
                user.setEmail(request.email());
                user.setPhone(request.phone());
                user.setCity(city);
                user.setSubCity(subCity);
                user.setWereda(wereda);
                user.getRoles().add(role);
                userId = userRepository.save(user).getId();
            }

            Teacher teacher = new Teacher();
            teacher.setOrganizationId(orgId);
            teacher.setName(request.fullName());
            teacher.setSubject(request.subject());
            teacher.setSchoolId(request.schoolId());
            teacher.setUserId(userId);

            UUID teacherId = teacherRepository.save(teacher).getId();
            auditService.record(
                    orgId,
                    current.getId(),
                    "SCHOOL_STUFF_CREATED",
                    "TEACHER",
                    teacherId,
                    java.util.Map.of("schoolId", request.schoolId().toString())
            );
            return teacherId;
        }

        if ("SCHOOL_DIRECTOR".equals(roleName)) {
            if (request.schoolId() == null) throw new IllegalArgumentException("schoolId is required for SCHOOL_DIRECTOR");
            if (request.username() == null || request.username().isBlank() || request.password() == null || request.password().isBlank()) {
                throw new IllegalArgumentException("username and password are required for SCHOOL_DIRECTOR");
            }
            if (!isSuperAdmin) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
            }

            User director = new User();
            director.setOrganizationId(orgId);
            director.setUsername(request.username());
            director.setPasswordHash(passwordEncoder.encode(request.password()));
            director.setFullName(request.fullName());
            director.setEmail(request.email());
            director.setPhone(request.phone());
            director.setCity(city);
            director.setSubCity(subCity);
            director.setWereda(wereda);
            director.getRoles().add(role);

            UUID directorId = userRepository.save(director).getId();

            School school = isSuperAdmin
                    ? schoolRepository.findByIdAndOrganizationId(request.schoolId(), orgId).orElseThrow(() -> new IllegalArgumentException("School not found"))
                    : schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));

            school.setDirectorUserId(directorId);
            schoolRepository.save(school);

            auditService.record(
                    orgId,
                    current.getId(),
                    "SCHOOL_STUFF_CREATED",
                    "SCHOOL_DIRECTOR",
                    directorId,
                    java.util.Map.of("schoolId", request.schoolId().toString())
            );
            return directorId;
        }

        // Other custom staff types: just create a user account with the chosen role.
        if (request.username() == null || request.username().isBlank() || request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("username and password are required for role type " + roleName);
        }

        User user = new User();
        user.setOrganizationId(orgId);
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setCity(city);
        user.setSubCity(subCity);
        user.setWereda(wereda);
        user.getRoles().add(role);

        UUID id = userRepository.save(user).getId();
        auditService.record(
                orgId,
                current.getId(),
                "SCHOOL_STUFF_CREATED",
                "STAFF",
                id,
                java.util.Map.of("role", roleName)
        );
        return id;
    }

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

    private void requireAdminOrCoordinator(User user) {
        boolean allowed = user.getRoles().stream().anyMatch(r ->
                "SUPER_ADMIN".equals(r.getName()) || "CLUSTER_COORDINATOR".equals(r.getName()));
        if (!allowed) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can manage school stuff");
        }
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
    }
}

