package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectRepository extends TenantAwareRepository<Subject, UUID> {
    Optional<Subject> findByOrganizationIdAndName(UUID organizationId, String name);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);

    List<Subject> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
}
