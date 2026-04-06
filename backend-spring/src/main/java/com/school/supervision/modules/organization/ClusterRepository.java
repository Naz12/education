package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClusterRepository extends TenantAwareRepository<Cluster, UUID> {
    long countByOrganizationIdAndWeredaId(UUID organizationId, UUID weredaId);
}
