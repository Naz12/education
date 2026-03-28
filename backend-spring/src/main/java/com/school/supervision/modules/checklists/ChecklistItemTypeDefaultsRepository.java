package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.DomainEnums;
import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistItemTypeDefaultsRepository extends TenantAwareRepository<ChecklistItemTypeDefaults, UUID> {
    Optional<ChecklistItemTypeDefaults> findByOrganizationIdAndItemType(UUID organizationId, DomainEnums.ChecklistItemType itemType);
}

