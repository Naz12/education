package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradeGroupRepository extends TenantAwareRepository<GradeGroup, UUID> {
    List<GradeGroup> findAllByOrganizationIdAndCoordinatorUserId(UUID organizationId, UUID coordinatorUserId);

    List<GradeGroup> findAllByOrganizationIdAndCoordinatorUserIdIsNull(UUID organizationId);

    Optional<GradeGroup> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
