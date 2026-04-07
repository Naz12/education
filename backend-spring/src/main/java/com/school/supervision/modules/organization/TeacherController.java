package com.school.supervision.modules.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.users.Role;
import com.school.supervision.modules.users.RoleRepository;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {
    private final TeacherRepository teacherRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SubjectRepository subjectRepository;
    private final PasswordEncoder passwordEncoder;
    private final AssignmentRepository assignmentRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TeacherController(TeacherRepository teacherRepository,
                             SchoolRepository schoolRepository,
                             UserRepository userRepository,
                             RoleRepository roleRepository,
                             SubjectRepository subjectRepository,
                             PasswordEncoder passwordEncoder,
                             AssignmentRepository assignmentRepository,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.teacherRepository = teacherRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.subjectRepository = subjectRepository;
        this.passwordEncoder = passwordEncoder;
        this.assignmentRepository = assignmentRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
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

    public record TeacherSummary(
            UUID id,
            String name,
            UUID subjectId,
            String subject,
            UUID schoolId,
            String schoolName,
            UUID userId,
            List<String> responsibleGradeCodes
    ) {}
    public record CreateTeacherRequest(
            @NotBlank String name,
            @NotNull UUID subjectId,
            @NotNull UUID schoolId,
            @NotNull List<String> responsibleGradeCodes,
            String username,
            String password,
            String email,
            String phone,
            String city,
            String subCity,
            String wereda
    ) {}

    public record UpdateTeacherRequest(
            @NotBlank String name,
            @NotNull UUID subjectId,
            @NotNull UUID schoolId,
            @NotNull List<String> responsibleGradeCodes
    ) {}

    @GetMapping
    public List<TeacherSummary> list(Authentication authentication, @RequestParam(required = false) UUID schoolId) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        List<Teacher> teachers;
        if (schoolId != null) {
            if (!isSuperAdmin(current)) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(schoolId, orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
            }
            teachers = teacherRepository.findAllByOrganizationIdAndSchoolId(orgId, schoolId);
        } else if (isSuperAdmin(current)) {
            teachers = teacherRepository.findAllByOrganizationId(orgId);
        } else {
            List<UUID> schoolIds = schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, current.getId())
                    .stream().map(School::getId).toList();
            teachers = schoolIds.isEmpty()
                    ? List.of()
                    : teacherRepository.findAllByOrganizationIdAndSchoolIdIn(orgId, schoolIds);
        }
        List<School> schoolsForNames = isSuperAdmin(current)
                ? schoolRepository.findAllByOrganizationId(orgId)
                : schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, current.getId());
        Map<UUID, String> schoolNames = schoolsForNames.stream()
                .collect(Collectors.toMap(School::getId, School::getName, (a, b) -> a));
        Set<UUID> subjectIds = new HashSet<>();
        for (Teacher t : teachers) {
            if (t.getSubjectId() != null) {
                subjectIds.add(t.getSubjectId());
            }
        }
        Map<UUID, String> subjectNames = subjectIds.isEmpty()
                ? Map.of()
                : subjectRepository.findAllByOrganizationIdAndIdIn(orgId, subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
        return teachers.stream()
                .map(t -> new TeacherSummary(
                        t.getId(),
                        t.getName(),
                        t.getSubjectId(),
                        subjectNames.get(t.getSubjectId()),
                        t.getSchoolId(),
                        schoolNames.get(t.getSchoolId()),
                        t.getUserId(),
                        GradeCodes.sortForDisplay(
                                GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, t.getResponsibleGradeCodesJson())))))
                .toList();
    }

    @PostMapping
    public UUID create(Authentication authentication, @Valid @RequestBody CreateTeacherRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        School schoolForTeacher = isSuperAdmin(current)
                ? schoolRepository.findByIdAndOrganizationId(request.schoolId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"))
                : schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
        subjectRepository.findByIdAndOrganizationId(request.subjectId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        UUID userId = null;
        if (request.username() != null && !request.username().isBlank() && request.password() != null && !request.password().isBlank()) {
            String loginUser = request.username().trim();
            if (userRepository.existsByUsernameAndOrganizationId(loginUser, orgId)) {
                throw new IllegalArgumentException("Username already exists in this organization.");
            }
            Role teacherRole = roleRepository.findByOrganizationIdAndName(orgId, "TEACHER")
                    .orElseThrow(() -> new IllegalArgumentException("Role TEACHER not found"));
            User user = new User();
            user.setOrganizationId(orgId);
            user.setUsername(loginUser);
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFullName(request.name());
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
            }
            user.getRoles().add(teacherRole);
            userId = userRepository.save(user).getId();
        }
        Teacher teacher = new Teacher();
        teacher.setOrganizationId(orgId);
        teacher.setName(request.name());
        teacher.setSubjectId(request.subjectId());
        teacher.setSchoolId(request.schoolId());
        teacher.setUserId(userId);
        teacher.setResponsibleGradeCodesJson(writeTeacherResponsibleGradesJson(schoolForTeacher, request.responsibleGradeCodes()));
        UUID id = teacherRepository.save(teacher).getId();
        auditService.record(
                orgId,
                current.getId(),
                "TEACHER_CREATED",
                "TEACHER",
                id,
                java.util.Map.of("schoolId", request.schoolId().toString())
        );
        return id;
    }

    @PatchMapping("/{teacherId}")
    public UUID update(Authentication authentication,
                        @org.springframework.web.bind.annotation.PathVariable UUID teacherId,
                        @Valid @RequestBody UpdateTeacherRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        Teacher teacher = teacherRepository.findByIdAndOrganizationId(teacherId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        // Scope enforcement for coordinators: teacher must be in their school set.
        if (!isSuperAdmin(current)) {
            schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(teacher.getSchoolId(), orgId, current.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found in coordinator scope"));
            schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), orgId, current.getId())
                    .orElseThrow(() -> new IllegalArgumentException("School not found in coordinator scope"));
        }

        subjectRepository.findByIdAndOrganizationId(request.subjectId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        School schoolForTeacher = schoolRepository.findByIdAndOrganizationId(request.schoolId(), orgId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));

        teacher.setName(request.name());
        teacher.setSubjectId(request.subjectId());
        teacher.setSchoolId(request.schoolId());
        teacher.setResponsibleGradeCodesJson(writeTeacherResponsibleGradesJson(schoolForTeacher, request.responsibleGradeCodes()));
        teacherRepository.save(teacher);

        auditService.record(
                orgId,
                current.getId(),
                "TEACHER_UPDATED",
                "TEACHER",
                teacherId,
                java.util.Map.of("schoolId", request.schoolId().toString())
        );
        return teacherId;
    }

    @DeleteMapping("/{teacherId}")
    public void delete(Authentication authentication,
                        @org.springframework.web.bind.annotation.PathVariable UUID teacherId) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();

        Teacher teacher = teacherRepository.findByIdAndOrganizationId(teacherId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        if (!isSuperAdmin(current)) {
            schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(teacher.getSchoolId(), orgId, current.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Teacher not found in coordinator scope"));
        }

        // Avoid breaking assignment references.
        List<Assignment> allAssignments = assignmentRepository.findAllByOrganizationId(orgId);
        boolean hasAssignments = allAssignments.stream()
                .anyMatch(a -> a.getTeacherId() != null && a.getTeacherId().equals(teacherId));
        if (hasAssignments) {
            throw new IllegalArgumentException("Cannot delete teacher that has assignments.");
        }

        teacherRepository.delete(teacher);
        auditService.record(
                orgId,
                current.getId(),
                "TEACHER_DELETED",
                "TEACHER",
                teacherId,
                java.util.Map.of()
        );
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
        boolean allowed = user.getRoles().stream()
                .anyMatch(r -> "SUPER_ADMIN".equals(r.getName()) || "CLUSTER_COORDINATOR".equals(r.getName()));
        if (!allowed) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can manage teachers");
        }
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
    }
}
