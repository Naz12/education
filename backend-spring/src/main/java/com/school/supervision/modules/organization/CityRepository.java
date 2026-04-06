package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CityRepository extends TenantAwareRepository<City, UUID> {
    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID excludeId);

    Optional<City> findByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}
