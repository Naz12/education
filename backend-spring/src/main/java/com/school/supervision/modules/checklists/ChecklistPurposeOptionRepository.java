package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistPurposeOptionRepository extends TenantAwareRepository<ChecklistPurposeOption, UUID> {
    Optional<ChecklistPurposeOption> findByOrganizationIdAndName(UUID organizationId, String name);

    List<ChecklistPurposeOption> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);
}
