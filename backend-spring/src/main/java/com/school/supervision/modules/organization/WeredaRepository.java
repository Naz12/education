package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WeredaRepository extends TenantAwareRepository<Wereda, UUID> {
    List<Wereda> findAllByOrganizationIdAndSubcityIdOrderByName(UUID organizationId, UUID subcityId);

    long countByOrganizationIdAndSubcityId(UUID organizationId, UUID subcityId);

    long countByOrganizationIdAndSubcityIdAndNameIgnoreCaseAndIdNot(
            UUID organizationId, UUID subcityId, String name, UUID excludeId);

    long countByOrganizationIdAndSubcityIdAndNameIgnoreCase(UUID organizationId, UUID subcityId, String name);
}
