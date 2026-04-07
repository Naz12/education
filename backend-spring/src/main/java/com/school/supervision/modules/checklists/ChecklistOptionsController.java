package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRoleChecks;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/checklists/options")
public class ChecklistOptionsController {
    private final ChecklistOptionService checklistOptionService;
    private final AuditService auditService;

    public ChecklistOptionsController(ChecklistOptionService checklistOptionService,
                                      AuditService auditService) {
        this.checklistOptionService = checklistOptionService;
        this.auditService = auditService;
    }

    private static User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User u)) {
            throw new AccessDeniedException("Authentication required");
        }
        return u;
    }

    private static void requireAdminOrCoordinator(User user) {
        if (!UserRoleChecks.isAdminOrCoordinator(user)) {
            throw new AccessDeniedException("Admin or coordinator required");
        }
    }

    private static UUID requireTenant() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return organizationId;
    }

    @GetMapping("/targets")
    public List<ChecklistDtos.TargetOptionResponse> listTargets(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        return checklistOptionService.listTargets();
    }

    @PostMapping("/targets")
    public UUID createTarget(Authentication authentication,
                             @Valid @RequestBody ChecklistDtos.CreateTargetOptionRequest request) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        UUID id = checklistOptionService.createTarget(request);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_TARGET_OPTION_CREATED",
                "CHECKLIST_TARGET_OPTION",
                id,
                Map.of("name", request.name().trim(), "routingKind", request.routingKind().name())
        );
        return id;
    }

    @PatchMapping("/targets/{id}")
    public void updateTarget(Authentication authentication,
                             @PathVariable UUID id,
                             @Valid @RequestBody ChecklistDtos.UpdateTargetOptionRequest request) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        checklistOptionService.updateTarget(id, request);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_TARGET_OPTION_UPDATED",
                "CHECKLIST_TARGET_OPTION",
                id,
                Map.of("name", request.name().trim(), "routingKind", request.routingKind().name())
        );
    }

    @DeleteMapping("/targets/{id}")
    public void deleteTarget(Authentication authentication, @PathVariable UUID id) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        checklistOptionService.deleteTarget(id);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_TARGET_OPTION_DELETED",
                "CHECKLIST_TARGET_OPTION",
                id,
                Map.of()
        );
    }

    @GetMapping("/purposes")
    public List<ChecklistDtos.PurposeOptionResponse> listPurposes(Authentication authentication) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        return checklistOptionService.listPurposes();
    }

    @PostMapping("/purposes")
    public UUID createPurpose(Authentication authentication,
                              @Valid @RequestBody ChecklistDtos.CreatePurposeOptionRequest request) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        UUID id = checklistOptionService.createPurpose(request);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_PURPOSE_OPTION_CREATED",
                "CHECKLIST_PURPOSE_OPTION",
                id,
                Map.of("name", request.name().trim())
        );
        return id;
    }

    @PatchMapping("/purposes/{id}")
    public void updatePurpose(Authentication authentication,
                              @PathVariable UUID id,
                              @Valid @RequestBody ChecklistDtos.UpdatePurposeOptionRequest request) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        checklistOptionService.updatePurpose(id, request);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_PURPOSE_OPTION_UPDATED",
                "CHECKLIST_PURPOSE_OPTION",
                id,
                Map.of("name", request.name().trim())
        );
    }

    @DeleteMapping("/purposes/{id}")
    public void deletePurpose(Authentication authentication, @PathVariable UUID id) {
        User user = requireCurrentUser(authentication);
        requireAdminOrCoordinator(user);
        checklistOptionService.deletePurpose(id);
        auditService.record(
                requireTenant(),
                user.getId(),
                "CHECKLIST_PURPOSE_OPTION_DELETED",
                "CHECKLIST_PURPOSE_OPTION",
                id,
                Map.of()
        );
    }
}
