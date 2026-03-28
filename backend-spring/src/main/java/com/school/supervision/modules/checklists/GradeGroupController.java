package com.school.supervision.modules.checklists;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.users.UserRoleChecks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/grade-groups")
public class GradeGroupController {
    private final GradeGroupRepository gradeGroupRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public GradeGroupController(GradeGroupRepository gradeGroupRepository,
                                UserRepository userRepository,
                                ObjectMapper objectMapper) {
        this.gradeGroupRepository = gradeGroupRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // Indicates whether the currently-authenticated cluster coordinator "owns" this grade group.
    // This is used by the UI to pre-fill/lock label + grades when creating new grade groups.
    public record GradeGroupSummary(UUID id, String displayName, String gradesDescription, List<String> gradeCodes, boolean managedByMe) {}

    public record CreateGradeGroupRequest(
            @NotBlank String displayName,
            /** Legacy free-text; used when gradeCodes is empty. */
            String gradesDescription,
            /** Preferred: canonical codes e.g. KG1, 1, 2 (see GET /api/meta/grade-codes). */
            List<String> gradeCodes
    ) {}

    @GetMapping
    public List<GradeGroupSummary> list(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        List<GradeGroup> merged = new ArrayList<>();
        if (UserRoleChecks.isSuperAdmin(current)) {
            merged.addAll(gradeGroupRepository.findAllByOrganizationId(orgId));
        } else {
            merged.addAll(gradeGroupRepository.findAllByOrganizationIdAndCoordinatorUserIdIsNull(orgId));
            merged.addAll(gradeGroupRepository.findAllByOrganizationIdAndCoordinatorUserId(orgId, current.getId()));
        }
        return merged.stream()
                .map(g -> new GradeGroupSummary(
                        g.getId(),
                        g.getDisplayName(),
                        g.getGradesDescription(),
                        GradeCodes.sortForDisplay(GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, g.getGradeCodesJson()))),
                        g.getCoordinatorUserId() != null && g.getCoordinatorUserId().equals(current.getId())
                ))
                .toList();
    }

    @PostMapping
    public UUID create(Authentication authentication, @Valid @RequestBody CreateGradeGroupRequest request) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        GradeGroup g = new GradeGroup();
        g.setOrganizationId(requireTenant());
        g.setDisplayName(request.displayName());
        if (request.gradeCodes() != null && !request.gradeCodes().isEmpty()) {
            Set<String> norm = GradeCodes.normalize(request.gradeCodes());
            if (norm.isEmpty()) {
                throw new IllegalArgumentException("No valid grade codes in gradeCodes");
            }
            try {
                g.setGradeCodesJson(objectMapper.writeValueAsString(GradeCodes.sortForDisplay(norm)));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Could not serialize grade codes");
            }
            String summary = GradeCodes.toSummary(norm);
            g.setGradesDescription(summary.isBlank() ? request.displayName() : summary);
        } else {
            if (request.gradesDescription() == null || request.gradesDescription().isBlank()) {
                throw new IllegalArgumentException("Provide gradeCodes or gradesDescription");
            }
            g.setGradesDescription(request.gradesDescription());
            g.setGradeCodesJson("[]");
        }
        if (!UserRoleChecks.isSuperAdmin(current)) {
            g.setCoordinatorUserId(current.getId());
        }
        return gradeGroupRepository.save(g).getId();
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
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can manage grade groups");
        }
    }

}
