package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistVersionRepository extends TenantAwareRepository<ChecklistVersion, UUID> {
    Optional<ChecklistVersion> findByChecklistIdAndVersionNoAndOrganizationId(UUID checklistId, Integer versionNo, UUID organizationId);
    List<ChecklistVersion> findAllByChecklistIdAndOrganizationIdOrderByVersionNoDesc(UUID checklistId, UUID organizationId);
}
