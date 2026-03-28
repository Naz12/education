package com.school.supervision.modules.assignments;

import com.school.supervision.common.domain.DomainEnums.AssignmentStatus;
import com.school.supervision.common.domain.DomainEnums.TargetType;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.checklists.ChecklistDtos;
import com.school.supervision.modules.checklists.ChecklistService;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.reviews.ReviewDtos;
import com.school.supervision.modules.reviews.ReviewService;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {
    private final AssignmentRepository assignmentRepository;
    private final ChecklistService checklistService;
    private final ReviewService reviewService;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;

    public AssignmentController(AssignmentRepository assignmentRepository,
                                ChecklistService checklistService,
                                ReviewService reviewService,
                                UserRepository userRepository,
                                SchoolRepository schoolRepository,
                                TeacherRepository teacherRepository,
                                AuditService auditService) {
        this.assignmentRepository = assignmentRepository;
        this.checklistService = checklistService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.auditService = auditService;
    }

    public record CreateAssignmentRequest(
            @NotNull UUID checklistId,
            @NotNull UUID checklistVersionId,
            @NotNull UUID supervisorId,
            @NotNull TargetType targetType,
            UUID schoolId,
            UUID teacherId,
            Instant dueDate
    ) {}

    public record UpdateAssignmentRequest(
            @NotNull UUID checklistId,
            @NotNull UUID checklistVersionId,
            @NotNull UUID supervisorId,
            @NotNull TargetType targetType,
            UUID schoolId,
            UUID teacherId,
            Instant dueDate
    ) {}

    @GetMapping
    public List<Assignment> list(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        if (isSuperAdmin(current)) {
            return assignmentRepository.findAllByOrganizationId(requireTenant());
        }
        return assignmentRepository.findAllByOrganizationIdAndCreatedBy(requireTenant(), current.getId());
    }

    @PostMapping
    public UUID create(Authentication authentication, @Valid @RequestBody CreateAssignmentRequest request) {
        User currentUser = requireCurrentUser(authentication);
        requireAdminOrCoordinator(currentUser);
        if (request.targetType() == TargetType.SCHOOL && request.schoolId() == null) {
            throw new IllegalArgumentException("schoolId is required for SCHOOL target assignments");
        }
        if (request.targetType() == TargetType.TEACHER && request.teacherId() == null) {
            throw new IllegalArgumentException("teacherId is required for TEACHER target assignments");
        }
        if (!isSuperAdmin(currentUser)) {
            User supervisor = userRepository.findByIdAndOrganizationId(request.supervisorId(), requireTenant())
                    .orElseThrow(() -> new IllegalArgumentException("Supervisor not found"));
            if (!currentUser.getId().equals(supervisor.getCoordinatorUserId())) {
                throw new IllegalArgumentException("Supervisor is outside coordinator scope");
            }
            if (request.targetType() == TargetType.SCHOOL) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), requireTenant(), currentUser.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School is outside coordinator scope"));
            }
            if (request.targetType() == TargetType.TEACHER) {
                Teacher teacher = teacherRepository.findByIdAndOrganizationId(request.teacherId(), requireTenant())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(teacher.getSchoolId(), requireTenant(), currentUser.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher is outside coordinator scope"));
            }
        }
        Assignment assignment = new Assignment();
        assignment.setOrganizationId(requireTenant());
        assignment.setChecklistId(request.checklistId());
        assignment.setChecklistVersionId(request.checklistVersionId());
        assignment.setSupervisorId(request.supervisorId());
        assignment.setTargetType(request.targetType());
        assignment.setSchoolId(request.schoolId());
        assignment.setTeacherId(request.teacherId());
        assignment.setDueDate(request.dueDate());
        assignment.setStatus(AssignmentStatus.PENDING);
        assignment.setCreatedBy(currentUser.getId());
        UUID assignmentId = assignmentRepository.save(assignment).getId();
        auditService.record(
                requireTenant(),
                currentUser.getId(),
                "ASSIGNMENT_CREATED",
                "ASSIGNMENT",
                assignmentId,
                java.util.Map.of("targetType", request.targetType().name(), "supervisorId", request.supervisorId().toString())
        );
        return assignmentId;
    }

    @PatchMapping("/{assignmentId}")
    public UUID update(Authentication authentication,
                        @PathVariable UUID assignmentId,
                        @Valid @RequestBody UpdateAssignmentRequest request) {
        User currentUser = requireCurrentUser(authentication);
        requireAdminOrCoordinator(currentUser);

        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(assignmentId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        // Coordinators can only edit assignments created by them.
        if (!isSuperAdmin(currentUser) && !assignment.getCreatedBy().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Assignment is outside your scope");
        }

        if (assignment.getStatus() != AssignmentStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING assignments can be edited");
        }

        // Validate target fields.
        if (request.targetType() == TargetType.SCHOOL && request.schoolId() == null) {
            throw new IllegalArgumentException("schoolId is required for SCHOOL target assignments");
        }
        if (request.targetType() == TargetType.TEACHER && request.teacherId() == null) {
            throw new IllegalArgumentException("teacherId is required for TEACHER target assignments");
        }

        // Coordinator-scoped validation: supervisor scope + target scope.
        if (!isSuperAdmin(currentUser)) {
            User supervisor = userRepository.findByIdAndOrganizationId(request.supervisorId(), requireTenant())
                    .orElseThrow(() -> new IllegalArgumentException("Supervisor not found"));
            if (!currentUser.getId().equals(supervisor.getCoordinatorUserId())) {
                throw new IllegalArgumentException("Supervisor is outside coordinator scope");
            }
            if (request.targetType() == TargetType.SCHOOL) {
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(request.schoolId(), requireTenant(), currentUser.getId())
                        .orElseThrow(() -> new IllegalArgumentException("School is outside coordinator scope"));
            }
            if (request.targetType() == TargetType.TEACHER) {
                Teacher teacher = teacherRepository.findByIdAndOrganizationId(request.teacherId(), requireTenant())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
                schoolRepository.findByIdAndOrganizationIdAndCoordinatorUserId(teacher.getSchoolId(), requireTenant(), currentUser.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher is outside coordinator scope"));
            }
        }

        assignment.setChecklistId(request.checklistId());
        assignment.setChecklistVersionId(request.checklistVersionId());
        assignment.setSupervisorId(request.supervisorId());
        assignment.setTargetType(request.targetType());
        assignment.setSchoolId(request.targetType() == TargetType.SCHOOL ? request.schoolId() : null);
        assignment.setTeacherId(request.targetType() == TargetType.TEACHER ? request.teacherId() : null);
        assignment.setDueDate(request.dueDate());
        assignmentRepository.save(assignment);

        auditService.record(
                requireTenant(),
                currentUser.getId(),
                "ASSIGNMENT_UPDATED",
                "ASSIGNMENT",
                assignmentId,
                java.util.Map.of("targetType", request.targetType().name(), "dueDate", request.dueDate() == null ? null : request.dueDate().toString())
        );
        return assignmentId;
    }

    @DeleteMapping("/{assignmentId}")
    public void delete(Authentication authentication, @PathVariable UUID assignmentId) {
        User currentUser = requireCurrentUser(authentication);
        requireAdminOrCoordinator(currentUser);
        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(assignmentId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));

        if (!isSuperAdmin(currentUser) && !assignment.getCreatedBy().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Assignment is outside your scope");
        }
        if (assignment.getStatus() != AssignmentStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING assignments can be deleted");
        }

        assignmentRepository.delete(assignment);
        auditService.record(
                requireTenant(),
                currentUser.getId(),
                "ASSIGNMENT_DELETED",
                "ASSIGNMENT",
                assignmentId,
                java.util.Map.of()
        );
    }

    @GetMapping("/my")
    public List<Assignment> myAssignments(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        boolean isSupervisor = user.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName()));
        if (!isSupervisor) {
            throw new AccessDeniedException("Only SUPERVISOR can access personal assignments");
        }
        return assignmentRepository.findAllBySupervisorIdAndOrganizationId(user.getId(), requireTenant());
    }

    @GetMapping("/{assignmentId}/render")
    public ChecklistDtos.RenderResponse render(@PathVariable UUID assignmentId,
                                                @RequestParam(name = "lang", required = false) String lang) {
        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(assignmentId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        return checklistService.renderChecklistVersion(assignment.getChecklistVersionId(), lang);
    }

    @PostMapping("/{assignmentId}/start")
    public UUID start(Authentication authentication, @PathVariable UUID assignmentId,
                      @Valid @RequestBody ReviewDtos.StartReviewRequest request) {
        User user = requireCurrentUser(authentication);
        UUID reviewId = reviewService.start(assignmentId, user.getId(), request);
        auditService.record(
                requireTenant(),
                user.getId(),
                "REVIEW_STARTED",
                "REVIEW",
                reviewId,
                java.util.Map.of("assignmentId", assignmentId.toString())
        );
        return reviewId;
    }

    @PostMapping("/{assignmentId}/submit")
    public UUID submit(Authentication authentication, @PathVariable UUID assignmentId,
                       @Valid @RequestBody ReviewDtos.SubmitReviewRequest request) {
        User user = requireCurrentUser(authentication);
        UUID reviewId = reviewService.submit(assignmentId, user.getId(), request);
        auditService.record(
                requireTenant(),
                user.getId(),
                "REVIEW_SUBMITTED",
                "REVIEW",
                reviewId,
                java.util.Map.of("assignmentId", assignmentId.toString(), "answersCount", request.answers().size())
        );
        return reviewId;
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
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can manage assignments");
        }
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
    }
}
