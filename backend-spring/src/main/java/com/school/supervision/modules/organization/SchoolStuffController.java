package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.users.Role;
import com.school.supervision.modules.users.RoleRepository;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
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
    private final AssignmentRepository assignmentRepository;

    public SchoolStuffController(
            RoleRepository roleRepository,
            UserRepository userRepository,
            TeacherRepository teacherRepository,
            SchoolRepository schoolRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            AssignmentRepository assignmentRepository
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.assignmentRepository = assignmentRepository;
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
    public record UpdateSchoolStuffRequest(
            @NotBlank String type,
            @NotBlank String fullName,
            String subject,
            UUID schoolId,
            String email,
            String phone,
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

    @PatchMapping("/{entryId}")
    public UUID update(Authentication authentication,
                       @PathVariable UUID entryId,
                       @Valid @RequestBody UpdateSchoolStuffRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        boolean isSuperAdmin = isSuperAdmin(current);
        String type = request.type().trim().toUpperCase();

        if ("TEACHER".equals(type)) {
            Teacher teacher = teacherRepository.findByIdAndOrganizationId(entryId, orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
            if (request.schoolId() == null) {
                throw new IllegalArgumentException("schoolId is required for TEACHER");
            }
            if (request.subject() == null || request.subject().isBlank()) {
                throw new IllegalArgumentException("subject is required for TEACHER");
            }
            if (!isSuperAdmin) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(teacher.getSchoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher not found in coordinator scope"));
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
            }
            teacher.setName(request.fullName().trim());
            teacher.setSubject(request.subject().trim());
            teacher.setSchoolId(request.schoolId());
            teacherRepository.save(teacher);
            if (teacher.getUserId() != null) {
                userRepository.findByIdAndOrganizationId(teacher.getUserId(), orgId).ifPresent(u -> {
                    u.setFullName(request.fullName().trim());
                    if (request.email() != null) {
                        String v = request.email().trim();
                        u.setEmail(v.isEmpty() ? null : v);
                    }
                    if (request.phone() != null) {
                        String v = request.phone().trim();
                        u.setPhone(v.isEmpty() ? null : v);
                    }
                    if (isSuperAdmin) {
                        if (request.city() != null) {
                            String v = request.city().trim();
                            u.setCity(v.isEmpty() ? null : v);
                        }
                        if (request.subCity() != null) {
                            String v = request.subCity().trim();
                            u.setSubCity(v.isEmpty() ? null : v);
                        }
                        if (request.wereda() != null) {
                            String v = request.wereda().trim();
                            u.setWereda(v.isEmpty() ? null : v);
                        }
                    }
                    userRepository.save(u);
                });
            }
            return entryId;
        }

        if ("SCHOOL_DIRECTOR".equals(type)) {
            User director = userRepository.findByIdAndOrganizationId(entryId, orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Director not found"));
            boolean hasRole = director.getRoles().stream().anyMatch(r -> "SCHOOL_DIRECTOR".equals(r.getName()));
            if (!hasRole) throw new IllegalArgumentException("User is not a SCHOOL_DIRECTOR");

            School currentSchool = schoolRepository.findAllByOrganizationId(orgId).stream()
                    .filter(s -> entryId.equals(s.getDirectorUserId()))
                    .findFirst()
                    .orElse(null);
            if (!isSuperAdmin && currentSchool != null) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(currentSchool.getId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Director not found in coordinator scope"));
            }

            director.setFullName(request.fullName().trim());
            if (request.email() != null) {
                String v = request.email().trim();
                director.setEmail(v.isEmpty() ? null : v);
            }
            if (request.phone() != null) {
                String v = request.phone().trim();
                director.setPhone(v.isEmpty() ? null : v);
            }
            if (isSuperAdmin) {
                if (request.city() != null) {
                    String v = request.city().trim();
                    director.setCity(v.isEmpty() ? null : v);
                }
                if (request.subCity() != null) {
                    String v = request.subCity().trim();
                    director.setSubCity(v.isEmpty() ? null : v);
                }
                if (request.wereda() != null) {
                    String v = request.wereda().trim();
                    director.setWereda(v.isEmpty() ? null : v);
                }
            }
            userRepository.save(director);

            if (request.schoolId() != null && (currentSchool == null || !request.schoolId().equals(currentSchool.getId()))) {
                School target = isSuperAdmin
                        ? schoolRepository.findByIdAndOrganizationId(request.schoolId(), orgId)
                            .orElseThrow(() -> new IllegalArgumentException("School not found"))
                        : schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                            .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
                if (currentSchool != null) {
                    currentSchool.setDirectorUserId(null);
                    schoolRepository.save(currentSchool);
                }
                target.setDirectorUserId(entryId);
                schoolRepository.save(target);
            }
            return entryId;
        }

        User staff = userRepository.findByIdAndOrganizationId(entryId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));
        boolean hasTypeRole = staff.getRoles().stream().anyMatch(r -> type.equals(r.getName()));
        if (!hasTypeRole) {
            throw new IllegalArgumentException("User does not have role " + type);
        }
        if (!isSuperAdmin) {
            if (!Objects.equals(staff.getCity(), current.getCity())
                    || !Objects.equals(staff.getSubCity(), current.getSubCity())
                    || !Objects.equals(staff.getWereda(), current.getWereda())) {
                throw new IllegalArgumentException("Staff not found in coordinator scope");
            }
        }
        staff.setFullName(request.fullName().trim());
        if (request.email() != null) {
            String v = request.email().trim();
            staff.setEmail(v.isEmpty() ? null : v);
        }
        if (request.phone() != null) {
            String v = request.phone().trim();
            staff.setPhone(v.isEmpty() ? null : v);
        }
        if (isSuperAdmin) {
            if (request.city() != null) {
                String v = request.city().trim();
                staff.setCity(v.isEmpty() ? null : v);
            }
            if (request.subCity() != null) {
                String v = request.subCity().trim();
                staff.setSubCity(v.isEmpty() ? null : v);
            }
            if (request.wereda() != null) {
                String v = request.wereda().trim();
                staff.setWereda(v.isEmpty() ? null : v);
            }
        }
        userRepository.save(staff);
        return entryId;
    }

    @DeleteMapping("/{entryId}")
    public void delete(Authentication authentication,
                       @PathVariable UUID entryId,
                       @RequestParam String type) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        boolean isSuperAdmin = isSuperAdmin(current);
        String normalizedType = type.trim().toUpperCase();

        if ("TEACHER".equals(normalizedType)) {
            Teacher teacher = teacherRepository.findByIdAndOrganizationId(entryId, orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
            if (!isSuperAdmin) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(teacher.getSchoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher not found in coordinator scope"));
            }
            if (assignmentRepository.existsByOrganizationIdAndTeacherId(orgId, entryId)) {
                throw new IllegalArgumentException("Cannot delete teacher that has assignments.");
            }
            teacherRepository.delete(teacher);
            return;
        }

        if ("SCHOOL_DIRECTOR".equals(normalizedType)) {
            User director = userRepository.findByIdAndOrganizationId(entryId, orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Director not found"));
            School linkedSchool = schoolRepository.findAllByOrganizationId(orgId).stream()
                    .filter(s -> entryId.equals(s.getDirectorUserId()))
                    .findFirst()
                    .orElse(null);
            if (linkedSchool != null) {
                if (!isSuperAdmin) {
                    schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(linkedSchool.getId(), orgId, current.getId())
                            .orElseThrow(() -> new IllegalArgumentException("Director not found in coordinator scope"));
                }
                if (assignmentRepository.existsByOrganizationIdAndSchoolId(orgId, linkedSchool.getId())) {
                    throw new IllegalArgumentException("Cannot delete director that has school assignments.");
                }
                linkedSchool.setDirectorUserId(null);
                schoolRepository.save(linkedSchool);
            }
            userRepository.delete(director);
            return;
        }

        User staff = userRepository.findByIdAndOrganizationId(entryId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Staff user not found"));
        if (!isSuperAdmin) {
            if (!Objects.equals(staff.getCity(), current.getCity())
                    || !Objects.equals(staff.getSubCity(), current.getSubCity())
                    || !Objects.equals(staff.getWereda(), current.getWereda())) {
                throw new IllegalArgumentException("Staff not found in coordinator scope");
            }
        }
        if (assignmentRepository.existsByOrganizationIdAndStaffUserId(orgId, entryId)) {
            throw new IllegalArgumentException("Cannot delete staff that has assignments.");
        }
        userRepository.delete(staff);
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

