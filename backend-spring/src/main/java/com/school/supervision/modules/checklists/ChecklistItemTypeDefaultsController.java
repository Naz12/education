package com.school.supervision.modules.checklists;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.domain.DomainEnums;
import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import com.school.supervision.modules.users.UserRoleChecks;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/checklists/type-defaults")
public class ChecklistItemTypeDefaultsController {
    private final ChecklistItemTypeDefaultsRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChecklistItemTypeDefaultsController(
            ChecklistItemTypeDefaultsRepository repository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public record TypeDefaultsResponse(
            DomainEnums.ChecklistItemType itemType,
            Map<String, Object> options,
            Map<String, Object> validation
    ) {}

    public record UpdateTypeDefaultsRequest(
            Map<String, Object> options,
            Map<String, Object> validation
    ) {}

    @GetMapping
    public List<TypeDefaultsResponse> list(Authentication authentication) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);

        UUID orgId = requireTenant();
        return Arrays.stream(DomainEnums.ChecklistItemType.values())
                .map(type -> {
                    ChecklistItemTypeDefaults row = repository
                            .findByOrganizationIdAndItemType(orgId, type)
                            .orElse(null);
                    Map<String, Object> options = row == null ? defaultOptions(type) : readJson(row.getOptionsJson());
                    Map<String, Object> validation = row == null ? defaultValidation(type) : readJson(row.getValidationJson());
                    return new TypeDefaultsResponse(type, options, validation);
                })
                .toList();
    }

    @PatchMapping("/{type}")
    public TypeDefaultsResponse update(
            Authentication authentication,
            @PathVariable DomainEnums.ChecklistItemType type,
            @Valid @RequestBody UpdateTypeDefaultsRequest request
    ) {
        User current = requireCurrentUser(authentication);
        requireAdminOrCoordinator(current);

        UUID orgId = requireTenant();
        Map<String, Object> mergedOptions = new HashMap<>(defaultOptions(type));
        if (request.options() != null) mergedOptions.putAll(request.options());
        Map<String, Object> mergedValidation = new HashMap<>(defaultValidation(type));
        if (request.validation() != null) mergedValidation.putAll(request.validation());

        String optionsJson = writeJson(mergedOptions);
        String validationJson = writeJson(mergedValidation);

        ChecklistItemTypeDefaults existing = repository
                .findByOrganizationIdAndItemType(orgId, type)
                .orElseGet(() -> {
                    ChecklistItemTypeDefaults n = new ChecklistItemTypeDefaults();
                    n.setOrganizationId(orgId);
                    n.setItemType(type);
                    return n;
                });

        existing.setOptionsJson(optionsJson);
        existing.setValidationJson(validationJson);
        repository.save(existing);

        return new TypeDefaultsResponse(type, mergedOptions, mergedValidation);
    }

    private UUID requireTenant() {
        UUID orgId = TenantContext.getOrganizationId();
        if (orgId == null) throw new IllegalStateException("Missing tenant context");
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
        boolean allowed = UserRoleChecks.isSuperAdmin(user) || UserRoleChecks.isCoordinator(user);
        if (!allowed) {
            throw new AccessDeniedException("Only SUPER_ADMIN or CLUSTER_COORDINATOR can manage item type defaults");
        }
    }

    private Map<String, Object> defaultOptions(DomainEnums.ChecklistItemType type) {
        return switch (type) {
            case YES_NO -> Map.of("choices", List.of("YES", "NO"));
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> Map.of("choices", List.of("Option 1", "Option 2"));
            case RATING -> Map.of();
            case TEXT, PHOTO -> Map.of();
        };
    }

    private Map<String, Object> defaultValidation(DomainEnums.ChecklistItemType type) {
        return switch (type) {
            case YES_NO, SINGLE_CHOICE, MULTIPLE_CHOICE -> Map.of("required", true);
            case RATING -> Map.of("required", true, "min", 1, "max", 5);
            case TEXT, PHOTO -> Map.of("required", true);
        };
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }
}

