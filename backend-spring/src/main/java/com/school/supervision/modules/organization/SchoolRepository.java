package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolRepository extends TenantAwareRepository<School, UUID> {
    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    List<School> findAllByOrganizationIdAndCoordinatorUserId(UUID organizationId, UUID coordinatorUserId);

    Optional<School> findByIdAndOrganizationIdAndCoordinatorUserId(UUID id, UUID organizationId, UUID coordinatorUserId);

    List<School> findAllByOrganizationIdAndNameContainingIgnoreCase(UUID organizationId, String name);

    List<School> findAllByOrganizationIdAndCoordinatorUserIdAndNameContainingIgnoreCase(
            UUID organizationId, UUID coordinatorUserId, String name);
}
