package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.DomainEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChecklistDtos {
    private ChecklistDtos() {}

    public record CreateChecklistRequest(
            @NotBlank String title,
            @NotNull DomainEnums.TargetType targetType,
            DomainEnums.DisplayMode displayMode,
            @NotNull DomainEnums.ChecklistPurpose purpose,
            @NotNull UUID gradeGroupId,
            /** When true (default), publishing creates school assignments for matching grades. */
            Boolean autoAssignOnPublish
    ) {}

    public record UpdateChecklistRequest(
            @NotBlank String title,
            @NotNull DomainEnums.TargetType targetType,
            @NotNull DomainEnums.ChecklistPurpose purpose,
            @NotNull UUID gradeGroupId,
            Boolean autoAssignOnPublish
    ) {}

    public record ItemRequest(
            @NotBlank String question,
            Map<String, String> questionLocalized,
            @NotNull DomainEnums.ChecklistItemType type,
            Map<String, Object> options,
            Map<String, Object> validation,
            String groupKey,
            @NotNull Integer order
    ) {}

    public record PublishVersionRequest(
            @NotNull List<ItemRequest> items,
            /** When true, skip automatic school assignments even if the checklist has auto-assign enabled. */
            Boolean skipAutoAssignment
    ) {}

    public record RenderItem(
            UUID id,
            String question,
            Map<String, Object> questionLocalized,
            String type,
            Map<String, Object> options,
            Map<String, Object> validation,
            String groupKey,
            Integer order
    ) {}

    public record RenderResponse(UUID checklistId, Integer version, String displayMode, List<RenderItem> items) {}
}
