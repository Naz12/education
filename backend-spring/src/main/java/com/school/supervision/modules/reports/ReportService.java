package com.school.supervision.modules.reports;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.checklists.Checklist;
import com.school.supervision.modules.checklists.ChecklistRepository;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import com.school.supervision.modules.reviews.FileAsset;
import com.school.supervision.modules.reviews.FileAssetRepository;
import com.school.supervision.modules.reviews.Review;
import com.school.supervision.modules.reviews.ReviewAnswer;
import com.school.supervision.modules.reviews.ReviewAnswerRepository;
import com.school.supervision.modules.reviews.ReviewRepository;
import com.school.supervision.modules.reviews.Signature;
import com.school.supervision.modules.reviews.SignatureRepository;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.users.UserRoleChecks;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ReportService {
    private final ReviewRepository reviewRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final SignatureRepository signatureRepository;
    private final FileAssetRepository fileAssetRepository;
    private final AssignmentRepository assignmentRepository;
    private final ChecklistRepository checklistRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;

    public ReportService(ReviewRepository reviewRepository,
                         ReviewAnswerRepository reviewAnswerRepository,
                         SignatureRepository signatureRepository,
                         FileAssetRepository fileAssetRepository,
                         AssignmentRepository assignmentRepository,
                         ChecklistRepository checklistRepository,
                         SchoolRepository schoolRepository,
                         TeacherRepository teacherRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.signatureRepository = signatureRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.assignmentRepository = assignmentRepository;
        this.checklistRepository = checklistRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
    }

    public List<SubmittedReportSummary> listSubmittedReviewSummaries(User currentUser) {
        UUID orgId = requireTenant();
        if (!UserRoleChecks.isSuperAdmin(currentUser)
                && !UserRoleChecks.isCoordinator(currentUser)
                && !UserRoleChecks.isSupervisor(currentUser)) {
            throw new AccessDeniedException("Not allowed to list reports");
        }
        List<Review> reviews = reviewRepository.findAllByOrganizationIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(orgId);
        Stream<Review> stream = reviews.stream();
        if (UserRoleChecks.isSuperAdmin(currentUser)) {
            // keep all
        } else if (UserRoleChecks.isCoordinator(currentUser)) {
            stream = stream.filter(r -> {
                User sup = userRepository.findByIdAndOrganizationId(r.getSupervisorId(), orgId).orElse(null);
                return sup != null && currentUser.getId().equals(sup.getCoordinatorUserId());
            });
        } else {
            stream = stream.filter(r -> r.getSupervisorId().equals(currentUser.getId()));
        }
        return stream.map(r -> toSummary(r, orgId)).toList();
    }

    private SubmittedReportSummary toSummary(Review review, UUID orgId) {
        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(review.getAssignmentId(), orgId)
                .orElseThrow(() -> new IllegalStateException("Assignment missing for review"));
        User supervisor = userRepository.findByIdAndOrganizationId(review.getSupervisorId(), orgId)
                .orElse(null);
        String supName = supervisor != null ? supervisor.getFullName() : "—";
        String supUser = supervisor != null ? supervisor.getUsername() : "—";

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
        String checklistTitle = checklistRepository.findByIdAndOrganizationId(assignment.getChecklistId(), orgId)
                .map(Checklist::getTitle)
                .orElse("—");
        String loc = review.getLocationStatus() == null ? null : review.getLocationStatus().name();

        return new SubmittedReportSummary(
                review.getId(),
                assignment.getId(),
                review.getSupervisorId(),
                supName,
                supUser,
                review.getStartedAt(),
                review.getCompletedAt(),
                assignment.getTargetType().name(),
                assignment.getSchoolId(),
                schoolName,
                assignment.getTeacherId(),
                teacherName,
                assignment.getChecklistId(),
                checklistTitle,
                loc,
                review.getDistanceFromSchool()
        );
    }

    public byte[] generateReviewReport(UUID reviewId, User currentUser) {
        UUID orgId = requireTenant();
        Review review = reviewRepository.findByIdAndOrganizationId(reviewId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        assertCanReadReview(currentUser, review, orgId);
        List<ReviewAnswer> answers = reviewAnswerRepository.findAllByReviewIdAndOrganizationId(reviewId, orgId);
        List<Signature> signatures = signatureRepository.findAllByReviewIdAndOrganizationId(reviewId, orgId);

        StringBuilder content = new StringBuilder();
        content.append("School Supervision Review Report\n");
        content.append("Review ID: ").append(reviewId).append("\n");
        content.append("Assignment ID: ").append(review.getAssignmentId()).append("\n");
        content.append("Answer Count: ").append(answers.size()).append("\n");
        content.append("Signatures:\n");
        for (Signature signature : signatures) {
            String url = fileAssetRepository.findByIdAndOrganizationId(signature.getFileAssetId(), orgId)
                    .map(FileAsset::getPublicUrl)
                    .orElse("N/A");
            content.append("- ").append(signature.getSignerRole()).append(": ").append(url).append("\n");
        }
        content.append("\nAnswers:\n");
        for (ReviewAnswer answer : answers) {
            content.append("- Item ").append(answer.getChecklistItemId()).append(": ")
                    .append(answer.getAnswerJson()).append("\n");
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void assertCanReadReview(User current, Review review, UUID orgId) {
        if (UserRoleChecks.isSuperAdmin(current)) {
            return;
        }
        if (UserRoleChecks.isSupervisor(current) && review.getSupervisorId().equals(current.getId())) {
            return;
        }
        if (UserRoleChecks.isCoordinator(current)) {
            User sup = userRepository.findByIdAndOrganizationId(review.getSupervisorId(), orgId)
                    .orElseThrow(() -> new AccessDeniedException("Supervisor not found"));
            if (current.getId().equals(sup.getCoordinatorUserId())) {
                return;
            }
        }
        throw new AccessDeniedException("You cannot access this report");
    }

    private UUID requireTenant() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return organizationId;
    }
}
