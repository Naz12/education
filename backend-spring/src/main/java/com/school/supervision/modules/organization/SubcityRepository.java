package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubcityRepository extends TenantAwareRepository<Subcity, UUID> {
    List<Subcity> findAllByOrganizationIdAndCityIdOrderByName(UUID organizationId, UUID cityId);

    long countByOrganizationIdAndCityId(UUID organizationId, UUID cityId);

    long countByOrganizationIdAndCityIdAndNameIgnoreCaseAndIdNot(
            UUID organizationId, UUID cityId, String name, UUID excludeId);

    long countByOrganizationIdAndCityIdAndNameIgnoreCase(UUID organizationId, UUID cityId, String name);
}
