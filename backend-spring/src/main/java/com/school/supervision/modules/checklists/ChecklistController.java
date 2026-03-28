package com.school.supervision.modules.checklists;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.users.UserRoleChecks;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/checklists")
public class ChecklistController {
    private final ChecklistService checklistService;
    private final ChecklistRepository checklistRepository;
    private final ChecklistVersionRepository checklistVersionRepository;
    private final GradeGroupRepository gradeGroupRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ChecklistController(ChecklistService checklistService,
                               ChecklistRepository checklistRepository,
                               ChecklistVersionRepository checklistVersionRepository,
                               GradeGroupRepository gradeGroupRepository,
                               UserRepository userRepository,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.checklistService = checklistService;
        this.checklistRepository = checklistRepository;
        this.checklistVersionRepository = checklistVersionRepository;
        this.gradeGroupRepository = gradeGroupRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public record ChecklistSummary(
            UUID id,
            String title,
            String targetType,
            String purpose,
            UUID gradeGroupId,
            String gradeGroupDisplayName,
            String gradesDescription,
            Integer activeVersion,
            boolean autoAssignOnPublish,
            List<String> gradeGroupGradeCodes
    ) {}
    public record ChecklistVersionSummary(UUID id, Integer versionNo, String status) {}

    @GetMapping
    public List<ChecklistSummary> list(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);
        UUID orgId = requireTenant();
        Map<UUID, GradeGroup> gradeMap = gradeGroupRepository.findAllByOrganizationId(orgId).stream()
                .collect(Collectors.toMap(GradeGroup::getId, Function.identity(), (a, b) -> a));
        List<Checklist> all = checklistRepository.findAllByOrganizationId(orgId);
        Set<UUID> coordinatorIds = all.stream()
                .map(Checklist::getCoordinatorUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> coordinatorMap = coordinatorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByOrganizationIdAndIdIn(orgId, coordinatorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<Checklist> list = all.stream()
                .filter(c -> UserRoleChecks.isSuperAdmin(current) || isChecklistInCoordinatorWereda(c, current, coordinatorMap))
                .toList();
        return list.stream()
                .map(c -> {
                    GradeGroup gg = c.getGradeGroupId() == null ? null : gradeMap.get(c.getGradeGroupId());
                    List<String> ggc = gg == null
                            ? List.of()
                            : GradeCodes.sortForDisplay(GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, gg.getGradeCodesJson())));
                    return new ChecklistSummary(
                            c.getId(),
                            c.getTitle(),
                            c.getTargetType().name(),
                            c.getPurpose() == null ? null : c.getPurpose().name(),
                            c.getGradeGroupId(),
                            gg != null ? gg.getDisplayName() : null,
                            gg != null ? gg.getGradesDescription() : c.getGradeScope(),
                            c.getActiveVersion(),
                            c.isAutoAssignOnPublish(),
                            ggc);
                })
                .toList();
    }

    @PostMapping
    public UUID create(Authentication authentication, @Valid @RequestBody ChecklistDtos.CreateChecklistRequest request) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        UUID coordinatorUserId = UserRoleChecks.isSuperAdmin(user) ? null : user.getId();
        UUID checklistId = checklistService.createChecklist(request, user.getId(), coordinatorUserId);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_CREATED",
                "CHECKLIST",
                checklistId,
                java.util.Map.of(
                        "title", request.title(),
                        "displayMode", request.displayMode() == null ? "GROUPED" : request.displayMode().name()
                )
        );
        return checklistId;
    }

    @PostMapping("/{checklistId}/versions")
    public UUID publish(Authentication authentication, @PathVariable UUID checklistId,
                        @Valid @RequestBody ChecklistDtos.PublishVersionRequest request) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        assertChecklistInScope(user, checklist);
        UUID versionId = checklistService.publishVersion(checklistId, request, user.getId());
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_PUBLISHED",
                "CHECKLIST_VERSION",
                versionId,
                java.util.Map.of("checklistId", checklistId.toString(), "itemsCount", request.items().size())
        );
        return versionId;
    }

    @PatchMapping("/{checklistId}")
    public UUID update(Authentication authentication,
                        @PathVariable UUID checklistId,
                        @Valid @RequestBody ChecklistDtos.UpdateChecklistRequest request) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        assertChecklistInScope(user, checklist);
        UUID coordinatorUserId = UserRoleChecks.isSuperAdmin(user) ? null : user.getId();
        checklistService.updateChecklist(checklistId, request, user.getId(), coordinatorUserId);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_UPDATED",
                "CHECKLIST",
                checklistId,
                java.util.Map.of(
                        "title", request.title(),
                        "targetType", request.targetType().name(),
                        "purpose", request.purpose().name(),
                        "gradeGroupId", request.gradeGroupId().toString()
                )
        );
        return checklistId;
    }

    @PatchMapping("/{checklistId}/disable")
    public void disable(Authentication authentication, @PathVariable UUID checklistId) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        assertChecklistInScope(user, checklist);
        checklistService.disableChecklist(checklistId);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_DISABLED",
                "CHECKLIST",
                checklistId,
                java.util.Map.of()
        );
    }

    @PatchMapping("/{checklistId}/enable")
    public void enable(Authentication authentication, @PathVariable UUID checklistId) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        assertChecklistInScope(user, checklist);
        checklistService.enableChecklist(checklistId);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_ENABLED",
                "CHECKLIST",
                checklistId,
                java.util.Map.of()
        );
    }

    @DeleteMapping("/{checklistId}")
    public void delete(Authentication authentication, @PathVariable UUID checklistId) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        assertChecklistInScope(user, checklist);
        checklistService.deleteChecklist(checklistId);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_DELETED",
                "CHECKLIST",
                checklistId,
                java.util.Map.of(
                        "title", checklist.getTitle()
                )
        );
    }

    @GetMapping("/{checklistId}/render")
    public ChecklistDtos.RenderResponse render(@PathVariable UUID checklistId,
                                                 @RequestParam(name = "lang", required = false) String lang) {
        return checklistService.render(checklistId, lang);
    }

    @GetMapping("/{checklistId}/versions")
    public List<ChecklistVersionSummary> versions(Authentication authentication, @PathVariable UUID checklistId) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        Checklist checklist = checklistRepository.findByIdAndOrganizationId(checklistId, requireTenant())
                .orElseThrow(() -> new IllegalArgumentException("Checklist not found"));
        assertChecklistInScope(user, checklist);
        return checklistVersionRepository.findAllByChecklistIdAndOrganizationIdOrderByVersionNoDesc(checklistId, requireTenant())
                .stream()
                .map(v -> new ChecklistVersionSummary(v.getId(), v.getVersionNo(), v.getStatus().name()))
                .toList();
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
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can manage checklists");
        }
    }

    private void assertChecklistInScope(User user, Checklist checklist) {
        if (UserRoleChecks.isSuperAdmin(user)) {
            return;
        }
        UUID coordinatorUserId = checklist.getCoordinatorUserId();
        if (coordinatorUserId == null) {
            throw new AccessDeniedException("Checklist outside your wereda scope");
        }
        if (isBlank(user.getWereda())) {
            if (coordinatorUserId.equals(user.getId())) {
                return;
            }
            throw new AccessDeniedException("Checklist outside your wereda scope");
        }
        User checklistCoordinator = userRepository.findByIdAndOrganizationId(coordinatorUserId, requireTenant())
                .orElseThrow(() -> new AccessDeniedException("Checklist coordinator not found"));
        if (isSameWereda(user, checklistCoordinator)) {
            return;
        }
        throw new AccessDeniedException("Checklist outside your wereda scope");
    }

    private boolean isChecklistInCoordinatorWereda(Checklist checklist, User current, Map<UUID, User> coordinatorMap) {
        UUID coordinatorUserId = checklist.getCoordinatorUserId();
        if (coordinatorUserId == null) return false;
        if (isBlank(current.getWereda())) {
            return coordinatorUserId.equals(current.getId());
        }
        User checklistCoordinator = coordinatorMap.get(coordinatorUserId);
        if (checklistCoordinator == null) return false;
        return isSameWereda(current, checklistCoordinator);
    }

    private boolean isSameWereda(User a, User b) {
        String wa = a.getWereda();
        String wb = b.getWereda();
        if (isBlank(wa) || isBlank(wb)) return false;
        return wa.trim().equalsIgnoreCase(wb.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
