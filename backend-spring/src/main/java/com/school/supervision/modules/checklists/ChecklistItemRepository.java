package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistItemRepository extends TenantAwareRepository<ChecklistItem, UUID> {
    List<ChecklistItem> findAllByChecklistVersionIdAndOrganizationIdOrderByDisplayOrder(UUID checklistVersionId, UUID organizationId);
}
