package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistTargetOptionRepository extends TenantAwareRepository<ChecklistTargetOption, UUID> {
    Optional<ChecklistTargetOption> findByOrganizationIdAndName(UUID organizationId, String name);

    List<ChecklistTargetOption> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);
}
