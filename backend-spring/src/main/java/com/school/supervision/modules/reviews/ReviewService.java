package com.school.supervision.modules.reviews;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.domain.DomainEnums;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.assignments.Assignment;
import com.school.supervision.modules.assignments.AssignmentRepository;
import com.school.supervision.modules.checklists.ChecklistDtos;
import com.school.supervision.modules.checklists.ChecklistService;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final AssignmentRepository assignmentRepository;
    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final ChecklistService checklistService;
    private final GeoValidationService geoValidationService;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final ObjectMapper objectMapper;

    public ReviewService(ReviewRepository reviewRepository,
                         AssignmentRepository assignmentRepository,
                         SchoolRepository schoolRepository,
                         TeacherRepository teacherRepository,
                         ChecklistService checklistService,
                         GeoValidationService geoValidationService,
                         ReviewAnswerRepository reviewAnswerRepository,
                         ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.assignmentRepository = assignmentRepository;
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.checklistService = checklistService;
        this.geoValidationService = geoValidationService;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID start(UUID assignmentId, UUID supervisorId, ReviewDtos.StartReviewRequest request) {
        UUID orgId = requireTenant();
        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(assignmentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        if (!assignment.getSupervisorId().equals(supervisorId)) {
            throw new IllegalArgumentException("Supervisor mismatch");
        }
        Optional<Review> existing = reviewRepository.findByAssignmentIdAndOrganizationId(assignmentId, orgId);
        if (existing.isPresent()) {
            if (existing.get().getCompletedAt() != null) {
                throw new IllegalArgumentException("Review already submitted");
            }
            return existing.get().getId();
        }
        Review review = new Review();
        review.setOrganizationId(orgId);
        review.setAssignmentId(assignmentId);
        review.setSupervisorId(supervisorId);
        review.setStartedAt(Instant.now());
        review.setStartLatitude(request.latitude());
        review.setStartLongitude(request.longitude());
        assignment.setStatus(DomainEnums.AssignmentStatus.IN_PROGRESS);
        assignmentRepository.save(assignment);
        return reviewRepository.save(review).getId();
    }

    @Transactional
    public UUID submit(UUID assignmentId, UUID supervisorId, ReviewDtos.SubmitReviewRequest request) {
        UUID orgId = requireTenant();
        Assignment assignment = assignmentRepository.findByIdAndOrganizationId(assignmentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        Review review = reviewRepository.findByAssignmentIdAndOrganizationId(assignmentId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Review not started"));
        if (!assignment.getSupervisorId().equals(supervisorId)) {
            throw new IllegalArgumentException("Supervisor mismatch");
        }
        if (review.getCompletedAt() != null) {
            throw new IllegalArgumentException("Review already submitted");
        }

        ChecklistDtos.RenderResponse render = checklistService.renderChecklistVersion(assignment.getChecklistVersionId(), "en");
        Map<UUID, Object> answersMap = new HashMap<>();
        for (ReviewDtos.AnswerPayload payload : request.answers()) {
            answersMap.put(payload.checklistItemId(), payload.answer());
        }
        List<String> validationErrors = checklistService.validateAnswers(render, answersMap);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException("Answer validation failed: " + validationErrors);
        }

        UUID schoolGeoId = assignment.getSchoolId();
        if (schoolGeoId == null && assignment.getTeacherId() != null) {
            schoolGeoId = teacherRepository.findByIdAndOrganizationId(assignment.getTeacherId(), orgId)
                    .map(Teacher::getSchoolId)
                    .orElse(null);
        }
        if (schoolGeoId == null) {
            throw new IllegalArgumentException("School not found for assignment geo validation");
        }
        School school = schoolRepository.findByIdAndOrganizationId(schoolGeoId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));
        GeoValidationService.ValidationResult geo = geoValidationService.validate(
                request.latitude(), request.longitude(), school, request.policy());
        if (geo.status() == DomainEnums.LocationStatus.BLOCKED) {
            throw new IllegalArgumentException("Submission blocked: outside allowed school radius");
        }

        review.setCompletedAt(Instant.now());
        review.setEndLatitude(request.latitude());
        review.setEndLongitude(request.longitude());
        review.setDistanceFromSchool(geo.distanceMeters());
        review.setIsWithinRange(geo.withinRange());
        review.setLocationStatus(geo.status());
        reviewRepository.save(review);
        assignment.setStatus(DomainEnums.AssignmentStatus.COMPLETED);
        assignmentRepository.save(assignment);

        for (ReviewDtos.AnswerPayload payload : request.answers()) {
            ReviewAnswer answer = new ReviewAnswer();
            answer.setOrganizationId(orgId);
            answer.setReviewId(review.getId());
            answer.setChecklistItemId(payload.checklistItemId());
            answer.setAnswerJson(toJson(payload.answer()));
            reviewAnswerRepository.save(answer);
        }
        return review.getId();
    }

    private UUID requireTenant() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return organizationId;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid answer payload", e);
        }
    }
}
