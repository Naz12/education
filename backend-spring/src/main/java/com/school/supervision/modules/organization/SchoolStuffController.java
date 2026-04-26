package com.school.supervision.modules.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.importexport.ExcelWorkbookService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.time.LocalDate;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/school-stuff")
public class SchoolStuffController {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolRepository schoolRepository;
    private final SubjectRepository subjectRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AssignmentRepository assignmentRepository;
    private final ObjectMapper objectMapper;
    private final ExcelWorkbookService excelWorkbookService;

    public SchoolStuffController(
            RoleRepository roleRepository,
            UserRepository userRepository,
            TeacherRepository teacherRepository,
            SchoolRepository schoolRepository,
            SubjectRepository subjectRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            AssignmentRepository assignmentRepository,
            ObjectMapper objectMapper,
            ExcelWorkbookService excelWorkbookService
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.schoolRepository = schoolRepository;
        this.subjectRepository = subjectRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.assignmentRepository = assignmentRepository;
        this.objectMapper = objectMapper;
        this.excelWorkbookService = excelWorkbookService;
    }

    private String writeTeacherResponsibleGradesJson(School school, List<String> raw) {
        Set<String> norm = GradeCodes.normalize(raw);
        if (norm.isEmpty()) {
            throw new IllegalArgumentException("Select at least one grade this staff is responsible for");
        }
        Set<String> schoolGrades = GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, school.getSupportedGradeCodesJson()));
        if (!schoolGrades.isEmpty()) {
            for (String g : norm) {
                if (!schoolGrades.contains(g)) {
                    throw new IllegalArgumentException("Each selected grade must be among this school's supported grades");
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(GradeCodes.sortForDisplay(norm));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not store responsible grades");
        }
    }

    public record SchoolStuffTypeSummary(UUID id, String name, String description, boolean systemRole) {}

    public record SchoolStuffSummary(
            UUID id,
            String type,
            String fullName,
            String username,
            String email,
            String phone,
            UUID subjectId,
            String subject,
            UUID schoolId,
            String schoolName,
            /** Populated for TEACHER rows; grades this teacher is responsible for. */
            List<String> responsibleGradeCodes,
            String city,
            String subCity,
            String wereda
    ) {}

    public record SubjectSummary(UUID id, String name) {}

    public record CreateSubjectRequest(@NotBlank String name) {}

    public record UpdateSubjectRequest(@NotBlank String name) {}

    public record UpdateSchoolStuffTypeRequest(@NotBlank String name, @NotBlank String description) {}

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
            UUID subjectId,
            /** Required when role is TEACHER; must be a non-empty subset of the school's supported grades when the school has grades configured. */
            List<String> responsibleGradeCodes,
            String city,
            String subCity,
            String wereda
    ) {}
    public record UpdateSchoolStuffRequest(
            @NotBlank String type,
            @NotBlank String fullName,
            UUID subjectId,
            UUID schoolId,
            /** Required when type is TEACHER. */
            List<String> responsibleGradeCodes,
            String email,
            String phone,
            String city,
            String subCity,
            String wereda
    ) {}

    public record BulkImportResult(int created, int skipped, int failed, List<Map<String, String>> errors) {}

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
                .map(r -> new SchoolStuffTypeSummary(r.getId(), r.getName(), r.getDescription(), r.isSystemRole()))
                .toList();
    }

    @PostMapping("/types")
    public UUID createType(Authentication authentication, @Valid @RequestBody CreateSchoolStuffTypeRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        Set<String> blocked = Set.of("SUPER_ADMIN", "CLUSTER_COORDINATOR", "SUPERVISOR");
        String typeName = request.name().trim().toUpperCase();
        if (blocked.contains(typeName)) {
            throw new IllegalArgumentException("Cannot create reserved role types");
        }

        if (roleRepository.findByOrganizationIdAndName(orgId, typeName).isPresent()) {
            throw new IllegalArgumentException("Role type already exists");
        }

        Role role = new Role();
        role.setOrganizationId(orgId);
        role.setName(typeName);
        role.setDescription(request.description().trim());
        role.setSystemRole(false);

        UUID id = roleRepository.save(role).getId();
        auditService.record(
                orgId,
                current.getId(),
                "SCHOOL_STUFF_TYPE_CREATED",
                "ROLE",
                id,
                java.util.Map.of("roleName", typeName)
        );
        return id;
    }

    @PatchMapping("/types/{typeId}")
    public void updateType(Authentication authentication,
                           @PathVariable UUID typeId,
                           @Valid @RequestBody UpdateSchoolStuffTypeRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        Role role = roleRepository.findByIdAndOrganizationId(typeId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Role type not found"));
        if (role.isSystemRole()) {
            throw new IllegalArgumentException("Cannot edit system role types");
        }
        Set<String> excluded = Set.of("SUPER_ADMIN", "CLUSTER_COORDINATOR", "SUPERVISOR");
        if (excluded.contains(role.getName())) {
            throw new IllegalArgumentException("Cannot edit reserved role types");
        }

        String newName = request.name().trim().toUpperCase();
        Set<String> blocked = Set.of("SUPER_ADMIN", "CLUSTER_COORDINATOR", "SUPERVISOR");
        if (blocked.contains(newName)) {
            throw new IllegalArgumentException("Cannot use reserved role type names");
        }
        roleRepository.findByOrganizationIdAndName(orgId, newName)
                .filter(r -> !r.getId().equals(typeId))
                .ifPresent(r -> {
                    throw new IllegalArgumentException("Role type already exists");
                });

        role.setName(newName);
        role.setDescription(request.description().trim());
        roleRepository.save(role);
        auditService.record(
                orgId,
                current.getId(),
                "SCHOOL_STUFF_TYPE_UPDATED",
                "ROLE",
                typeId,
                java.util.Map.of("roleName", newName)
        );
    }

    @DeleteMapping("/types/{typeId}")
    public void deleteType(Authentication authentication, @PathVariable UUID typeId) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        Role role = roleRepository.findByIdAndOrganizationId(typeId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Role type not found"));
        if (role.isSystemRole()) {
            throw new IllegalArgumentException("Cannot delete system role types");
        }
        Set<String> excluded = Set.of("SUPER_ADMIN", "CLUSTER_COORDINATOR", "SUPERVISOR");
        if (excluded.contains(role.getName())) {
            throw new IllegalArgumentException("Cannot delete reserved role types");
        }
        long usersWithRole = userRepository.countByOrganizationIdAndRoleId(orgId, typeId);
        if (usersWithRole > 0) {
            throw new IllegalArgumentException("Cannot delete role type that is assigned to users");
        }
        roleRepository.delete(role);
        auditService.record(
                orgId,
                current.getId(),
                "SCHOOL_STUFF_TYPE_DELETED",
                "ROLE",
                typeId,
                java.util.Map.of("roleName", role.getName())
        );
    }

    @GetMapping("/subjects")
    public List<SubjectSummary> listSubjects(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        return subjectRepository.findAllByOrganizationId(orgId).stream()
                .sorted(Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new SubjectSummary(s.getId(), s.getName()))
                .toList();
    }

    @PostMapping("/subjects")
    public UUID createSubject(Authentication authentication, @Valid @RequestBody CreateSubjectRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Subject name is required");
        }
        if (subjectRepository.existsByOrganizationIdAndName(orgId, name)) {
            throw new IllegalArgumentException("Subject already exists");
        }
        Subject s = new Subject();
        s.setOrganizationId(orgId);
        s.setName(name);
        UUID id = subjectRepository.save(s).getId();
        auditService.record(
                orgId,
                current.getId(),
                "SUBJECT_CREATED",
                "SUBJECT",
                id,
                java.util.Map.of("name", name)
        );
        return id;
    }

    @PatchMapping("/subjects/{subjectId}")
    public void updateSubject(Authentication authentication,
                              @PathVariable UUID subjectId,
                              @Valid @RequestBody UpdateSubjectRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        Subject s = subjectRepository.findByIdAndOrganizationId(subjectId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Subject name is required");
        }
        subjectRepository.findByOrganizationIdAndName(orgId, name)
                .filter(x -> !x.getId().equals(subjectId))
                .ifPresent(x -> {
                    throw new IllegalArgumentException("Subject already exists");
                });
        s.setName(name);
        subjectRepository.save(s);
        auditService.record(
                orgId,
                current.getId(),
                "SUBJECT_UPDATED",
                "SUBJECT",
                subjectId,
                java.util.Map.of("name", name)
        );
    }

    @DeleteMapping("/subjects/{subjectId}")
    public void deleteSubject(Authentication authentication, @PathVariable UUID subjectId) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        Subject s = subjectRepository.findByIdAndOrganizationId(subjectId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        if (teacherRepository.countByOrganizationIdAndSubjectId(orgId, subjectId) > 0) {
            throw new IllegalArgumentException("Cannot delete subject that is assigned to teachers");
        }
        subjectRepository.delete(s);
        auditService.record(
                orgId,
                current.getId(),
                "SUBJECT_DELETED",
                "SUBJECT",
                subjectId,
                java.util.Map.of("name", s.getName())
        );
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

        Set<UUID> teacherSubjectIds = teachersInScope.stream()
                .map(Teacher::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> subjectNameById = teacherSubjectIds.isEmpty()
                ? Map.of()
                : subjectRepository.findAllByOrganizationIdAndIdIn(orgId, teacherSubjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));

        for (Teacher t : teachersInScope) {
            User u = null;
            if (t.getUserId() != null) {
                u = userRepository.findByIdAndOrganizationId(t.getUserId(), orgId).orElse(null);
            }
            School s = schoolById.get(t.getSchoolId());
            UUID sid = t.getSubjectId();
            List<String> teacherGrades = GradeCodes.sortForDisplay(
                    GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, t.getResponsibleGradeCodesJson())));
            result.add(new SchoolStuffSummary(
                    t.getId(),
                    "TEACHER",
                    t.getName(),
                    u != null ? u.getUsername() : null,
                    u != null ? u.getEmail() : null,
                    u != null ? u.getPhone() : null,
                    sid,
                    sid != null ? subjectNameById.get(sid) : null,
                    t.getSchoolId(),
                    s != null ? s.getName() : null,
                    teacherGrades,
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
                    null,
                    s != null ? s.getId() : null,
                    s != null ? s.getName() : null,
                    null,
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

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        List<String> headers = List.of(
                "type", "fullName", "username", "password", "email", "phone",
                "schoolNameOrId", "subjectNameOrId", "responsibleGradeCodes",
                "city", "subCity", "wereda"
        );
        List<List<String>> sample = List.of(
                List.of("TEACHER", "Sample Teacher", "teacher.sample", "12345678", "t@example.com", "0911000000",
                        "Sample School", "Mathematics", "KG1,KG2", "", "", ""),
                List.of("SCHOOL_DIRECTOR", "Sample Director", "director.sample", "12345678", "d@example.com", "0911000001",
                        "Sample School", "", "", "", "", ""),
                List.of("LIBRARIAN", "Sample Staff", "staff.sample", "12345678", "s@example.com", "0911000002",
                        "", "", "", "", "", "")
        );
        List<String> notes = List.of(
                "type is required and must match a configured school-stuff type (TEACHER, SCHOOL_DIRECTOR, or custom type).",
                "TEACHER requires schoolNameOrId, subjectNameOrId, responsibleGradeCodes. username/password optional (both together).",
                "SCHOOL_DIRECTOR requires schoolNameOrId and username/password.",
                "Custom staff types require username/password."
        );
        byte[] bytes = excelWorkbookService.buildTemplate("school-stuff", headers, sample, notes);
        return xlsxAttachment("school-stuff-template.xlsx", bytes);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication) {
        List<SchoolStuffSummary> rowsData = list(authentication);
        List<String> headers = List.of(
                "id", "type", "fullName", "username", "email", "phone",
                "schoolId", "schoolName", "subjectId", "subject",
                "responsibleGradeCodes", "city", "subCity", "wereda"
        );
        List<List<String>> rows = rowsData.stream().map(r -> List.of(
                r.id().toString(),
                nullToEmpty(r.type()),
                nullToEmpty(r.fullName()),
                nullToEmpty(r.username()),
                nullToEmpty(r.email()),
                nullToEmpty(r.phone()),
                r.schoolId() == null ? "" : r.schoolId().toString(),
                nullToEmpty(r.schoolName()),
                r.subjectId() == null ? "" : r.subjectId().toString(),
                nullToEmpty(r.subject()),
                String.join(",", r.responsibleGradeCodes() == null ? List.of() : r.responsibleGradeCodes()),
                nullToEmpty(r.city()),
                nullToEmpty(r.subCity()),
                nullToEmpty(r.wereda())
        )).toList();
        byte[] bytes = excelWorkbookService.buildExport("school-stuff", headers, rows);
        return xlsxAttachment("school-stuff-export-" + LocalDate.now() + ".xlsx", bytes);
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
                String type = required(row, "type").trim().toUpperCase();
                Role role = roleRepository.findByOrganizationIdAndName(orgId, type)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown type: " + type));
                String fullName = required(row, "fullName");
                String username = nullable(row.get("username"));
                if (username != null && userRepository.existsByUsernameAndOrganizationId(username, orgId)) {
                    skipped++;
                    continue;
                }
                UUID schoolId = resolveSchoolId(current, row.get("schoolNameOrId"));
                UUID subjectId = resolveSubjectId(orgId, row.get("subjectNameOrId"));
                if ("TEACHER".equals(type)
                        && schoolId != null
                        && subjectId != null
                        && teacherRepository.existsByOrganizationIdAndSchoolIdAndSubjectIdAndNameIgnoreCase(
                        orgId, schoolId, subjectId, fullName)) {
                    skipped++;
                    continue;
                }
                if ("SCHOOL_DIRECTOR".equals(type) && schoolId != null) {
                    School school = schoolRepository.findByIdAndOrganizationId(schoolId, orgId)
                            .orElseThrow(() -> new IllegalArgumentException("School not found"));
                    if (school.getDirectorUserId() != null) {
                        skipped++;
                        continue;
                    }
                }
                CreateSchoolStuffRequest req = new CreateSchoolStuffRequest(
                        role.getId(),
                        fullName,
                        username,
                        nullable(row.get("password")),
                        nullable(row.get("email")),
                        nullable(row.get("phone")),
                        schoolId,
                        subjectId,
                        splitCsv(row.get("responsibleGradeCodes")),
                        nullable(row.get("city")),
                        nullable(row.get("subCity")),
                        nullable(row.get("wereda"))
                );
                create(authentication, req);
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
            if (request.subjectId() == null) throw new IllegalArgumentException("subjectId is required for TEACHER");
            subjectRepository.findByIdAndOrganizationId(request.subjectId(), orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
            School schoolForTeacher = isSuperAdmin
                    ? schoolRepository.findByIdAndOrganizationId(request.schoolId(), orgId)
                    .orElseThrow(() -> new IllegalArgumentException("School not found"))
                    : schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                            .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));

            UUID userId = null;
            boolean hasLogin = request.username() != null && !request.username().isBlank();
            boolean hasPassword = request.password() != null && !request.password().isBlank();
            if (hasLogin || hasPassword) {
                if (!hasLogin || !hasPassword) {
                    throw new IllegalArgumentException("username and password are required together for TEACHER login");
                }
                String loginUser = request.username().trim();
                if (userRepository.existsByUsernameAndOrganizationId(loginUser, orgId)) {
                    throw new IllegalArgumentException("Username already exists in this organization.");
                }
                User user = new User();
                user.setOrganizationId(orgId);
                user.setUsername(loginUser);
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
            teacher.setSubjectId(request.subjectId());
            teacher.setSchoolId(request.schoolId());
            teacher.setUserId(userId);
            teacher.setResponsibleGradeCodesJson(writeTeacherResponsibleGradesJson(schoolForTeacher, request.responsibleGradeCodes()));

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

            String directorLogin = request.username().trim();
            if (userRepository.existsByUsernameAndOrganizationId(directorLogin, orgId)) {
                throw new IllegalArgumentException("Username already exists in this organization.");
            }
            User director = new User();
            director.setOrganizationId(orgId);
            director.setUsername(directorLogin);
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

        String staffLogin = request.username().trim();
        if (userRepository.existsByUsernameAndOrganizationId(staffLogin, orgId)) {
            throw new IllegalArgumentException("Username already exists in this organization.");
        }
        User user = new User();
        user.setOrganizationId(orgId);
        user.setUsername(staffLogin);
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
            if (request.subjectId() == null) {
                throw new IllegalArgumentException("subjectId is required for TEACHER");
            }
            subjectRepository.findByIdAndOrganizationId(request.subjectId(), orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
            if (!isSuperAdmin) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(teacher.getSchoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher not found in coordinator scope"));
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
            }
            if (request.responsibleGradeCodes() == null) {
                throw new IllegalArgumentException("responsibleGradeCodes is required for TEACHER");
            }
            School schoolForTeacher = schoolRepository.findByIdAndOrganizationId(request.schoolId(), orgId)
                    .orElseThrow(() -> new IllegalArgumentException("School not found"));
            teacher.setName(request.fullName().trim());
            teacher.setSubjectId(request.subjectId());
            teacher.setSchoolId(request.schoolId());
            teacher.setResponsibleGradeCodesJson(writeTeacherResponsibleGradesJson(schoolForTeacher, request.responsibleGradeCodes()));
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
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private UUID resolveSchoolId(User current, String schoolNameOrId) {
        String raw = nullable(schoolNameOrId);
        if (raw == null) return null;
        UUID orgId = requireTenant();
        boolean superAdmin = isSuperAdmin(current);
        try {
            UUID schoolId = UUID.fromString(raw);
            return superAdmin
                    ? schoolRepository.findByIdAndOrganizationId(schoolId, orgId)
                        .orElseThrow(() -> new IllegalArgumentException("School not found")).getId()
                    : schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(schoolId, orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope")).getId();
        } catch (IllegalArgumentException ignored) {
            List<School> scoped = superAdmin
                    ? schoolRepository.findAllByOrganizationIdAndNameContainingIgnoreCase(orgId, raw)
                    : schoolRepository.findAllByOrganizationIdAndCoordinatorUserIdAndNameContainingIgnoreCase(orgId, current.getId(), raw);
            for (School s : scoped) {
                if (s.getName() != null && s.getName().equalsIgnoreCase(raw)) return s.getId();
            }
            throw new IllegalArgumentException("School not found: " + raw);
        }
    }

    private UUID resolveSubjectId(UUID orgId, String subjectNameOrId) {
        String raw = nullable(subjectNameOrId);
        if (raw == null) return null;
        try {
            UUID subjectId = UUID.fromString(raw);
            return subjectRepository.findByIdAndOrganizationId(subjectId, orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Subject not found")).getId();
        } catch (IllegalArgumentException ignored) {
            return subjectRepository.findAllByOrganizationId(orgId).stream()
                    .filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(raw))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + raw))
                    .getId();
        }
    }

    private String inferFieldFromMessage(String msg) {
        if (msg == null) return "row";
        String m = msg.toLowerCase();
        if (m.contains("type") || m.contains("role")) return "type";
        if (m.contains("full")) return "fullName";
        if (m.contains("username")) return "username";
        if (m.contains("password")) return "password";
        if (m.contains("school")) return "schoolNameOrId";
        if (m.contains("subject")) return "subjectNameOrId";
        if (m.contains("grade")) return "responsibleGradeCodes";
        return "row";
    }

    private String safeMessage(Exception e) {
        return (e.getMessage() == null || e.getMessage().isBlank()) ? "Invalid row data" : e.getMessage();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

