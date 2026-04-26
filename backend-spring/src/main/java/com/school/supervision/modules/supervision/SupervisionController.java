package com.school.supervision.modules.supervision;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.checklists.Checklist;
import com.school.supervision.modules.checklists.ChecklistRepository;
import com.school.supervision.modules.importexport.ExcelWorkbookService;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import com.school.supervision.modules.reviews.Review;
import com.school.supervision.modules.reviews.ReviewRepository;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.users.UserRoleChecks;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supervision")
public class SupervisionController {
    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final ReviewRepository reviewRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final ChecklistRepository checklistRepository;
    private final SupervisionStatsService supervisionStatsService;
    private final ExcelWorkbookService excelWorkbookService;

    public SupervisionController(UserRepository userRepository,
                                 AssignmentRepository assignmentRepository,
                                 ReviewRepository reviewRepository,
                                 SchoolRepository schoolRepository,
                                 TeacherRepository teacherRepository,
                                 ChecklistRepository checklistRepository,
                                 SupervisionStatsService supervisionStatsService,
                                 ExcelWorkbookService excelWorkbookService) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.reviewRepository = reviewRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.checklistRepository = checklistRepository;
        this.supervisionStatsService = supervisionStatsService;
        this.excelWorkbookService = excelWorkbookService;
    }

    public record SupervisorSummaryResponse(
            UUID supervisorId,
            String fullName,
            String username,
            long completedAssignments,
            long pendingAssignments,
            long inProgressAssignments,
            long overdueAssignments,
            long visitsCompleted
    ) {}

    public record VisitDetailResponse(
            UUID reviewId,
            UUID assignmentId,
            Instant startedAt,
            Instant completedAt,
            Instant dueDate,
            String targetType,
            String targetGradeCode,
            UUID schoolId,
            String schoolName,
            UUID teacherId,
            String teacherName,
            UUID staffUserId,
            String staffFullName,
            UUID checklistId,
            String checklistTitle,
            String locationStatus,
            Double distanceFromSchoolMeters
    ) {}

    @GetMapping("/my-workload")
    public SupervisionStatsService.MyWorkloadResponse myWorkload(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        if (!UserRoleChecks.isSupervisor(user)) {
            throw new AccessDeniedException("Only SUPERVISOR can view workload");
        }
        UUID orgId = requireTenant();
        return supervisionStatsService.buildWorkload(user.getId(), orgId);
    }

    @GetMapping("/supervisor-summaries")
    public List<SupervisorSummaryResponse> supervisorSummaries(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        if (!UserRoleChecks.isAdminOrCoordinator(current)) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can view summaries");
        }
        UUID orgId = requireTenant();
        List<User> supervisors = userRepository.findAllByOrganizationId(orgId).stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> "SUPERVISOR".equals(r.getName())))
                .filter(u -> UserRoleChecks.isSuperAdmin(current)
                        || (u.getCoordinatorUserId() != null && u.getCoordinatorUserId().equals(current.getId())))
                .toList();
        return supervisors.stream()
                .map(u -> {
                    SupervisionStatsService.MyWorkloadResponse w = supervisionStatsService.buildWorkload(u.getId(), orgId);
                    return new SupervisorSummaryResponse(
                            u.getId(),
                            u.getFullName(),
                            u.getUsername(),
                            w.completedAssignments(),
                            w.pendingAssignments(),
                            w.inProgressAssignments(),
                            w.overdueAssignments(),
                            w.visitsCompleted()
                    );
                })
                .toList();
    }

    @GetMapping("/supervisors/{supervisorId}/visits")
    public List<VisitDetailResponse> supervisorVisits(Authentication authentication, @PathVariable UUID supervisorId) {
        User current = requireCurrentUser(authentication);
        if (!UserRoleChecks.isAdminOrCoordinator(current)) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can view visit details");
        }
        UUID orgId = requireTenant();
        User supervisor = userRepository.findByIdAndOrganizationId(supervisorId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Supervisor not found"));
        if (!UserRoleChecks.isSuperAdmin(current) && !current.getId().equals(supervisor.getCoordinatorUserId())) {
            throw new AccessDeniedException("Supervisor outside your scope");
        }
        List<Review> reviews = reviewRepository
                .findAllByOrganizationIdAndSupervisorIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(orgId, supervisorId);
        return reviews.stream().map(r -> toVisitDetail(r, orgId)).toList();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication, @RequestParam(required = false) UUID supervisorId) {
        User current = requireCurrentUser(authentication);
        if (!UserRoleChecks.isAdminOrCoordinator(current)) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can export supervision data");
        }
        List<VisitDetailResponse> visits;
        if (supervisorId != null) {
            visits = supervisorVisits(authentication, supervisorId);
        } else {
            visits = supervisorSummaries(authentication).stream()
                    .flatMap(s -> supervisorVisits(authentication, s.supervisorId()).stream())
                    .toList();
        }
        List<String> headers = List.of(
                "reviewId", "assignmentId", "supervisorId", "supervisorName",
                "completedAt", "dueDate", "checklist", "targetType", "targetGrade",
                "school", "teacher", "staff", "locationStatus", "distanceFromSchoolMeters"
        );
        UUID orgId = requireTenant();
        List<List<String>> rows = visits.stream().map(v -> {
            UUID supId = assignmentRepository.findByIdAndOrganizationId(v.assignmentId(), orgId).map(Assignment::getSupervisorId).orElse(null);
            String supName = supId == null ? "" : userRepository.findByIdAndOrganizationId(supId, orgId).map(User::getFullName).orElse("");
            return List.of(
                    v.reviewId().toString(),
                    v.assignmentId().toString(),
                    supId == null ? "" : supId.toString(),
                    supName,
                    v.completedAt() == null ? "" : v.completedAt().toString(),
                    v.dueDate() == null ? "" : v.dueDate().toString(),
                    v.checklistTitle() == null ? "" : v.checklistTitle(),
                    v.targetType() == null ? "" : v.targetType(),
                    v.targetGradeCode() == null ? "" : v.targetGradeCode(),
                    v.schoolName() == null ? "" : v.schoolName(),
                    v.teacherName() == null ? "" : v.teacherName(),
                    v.staffFullName() == null ? "" : v.staffFullName(),
                    v.locationStatus() == null ? "" : v.locationStatus(),
                    v.distanceFromSchoolMeters() == null ? "" : String.valueOf(v.distanceFromSchoolMeters())
            );
        }).toList();
        byte[] bytes = excelWorkbookService.buildExport("activity", headers, rows);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-export-" + LocalDate.now() + ".xlsx\"")
                .body(bytes);
    }

    private VisitDetailResponse toVisitDetail(Review review, UUID orgId) {
        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(review.getAssignmentId(), orgId)
                .orElseThrow(() -> new IllegalStateException("Assignment missing for review"));
        String schoolName = null;
        if (assignment.getSchoolId() != null) {
            schoolName = schoolRepository.findByIdAndOrganizationId(assignment.getSchoolId(), orgId)
                    .map(School::getName)
                    .orElse(null);
        }
        String teacherName = null;
        if (assignment.getTeacherId() != null) {
            teacherName = teacherRepository.findByIdAndOrganizationId(assignment.getTeacherId(), orgId)
                    .map(Teacher::getName)
                    .orElse(null);
        }
        String staffFullName = null;
        if (assignment.getStaffUserId() != null) {
            staffFullName = userRepository.findByIdAndOrganizationId(assignment.getStaffUserId(), orgId)
                    .map(User::getFullName)
                    .orElse(null);
        }
        String checklistTitle = checklistRepository.findByIdAndOrganizationId(assignment.getChecklistId(), orgId)
                .map(Checklist::getTitle)
                .orElse("—");
        String loc = review.getLocationStatus() == null ? null : review.getLocationStatus().name();
        return new VisitDetailResponse(
                review.getId(),
                assignment.getId(),
                review.getStartedAt(),
                review.getCompletedAt(),
                assignment.getDueDate(),
                assignment.getTargetType().name(),
                assignment.getTargetGradeCode(),
                assignment.getSchoolId(),
                schoolName,
                assignment.getTeacherId(),
                teacherName,
                assignment.getStaffUserId(),
                staffFullName,
                assignment.getChecklistId(),
                checklistTitle,
                loc,
                review.getDistanceFromSchool()
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
}
