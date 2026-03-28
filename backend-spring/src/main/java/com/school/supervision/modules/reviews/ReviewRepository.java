package com.school.supervision.modules.reviews;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends TenantAwareRepository<Review, UUID> {
    Optional<Review> findByAssignmentIdAndOrganizationId(UUID assignmentId, UUID organizationId);
    Optional<Review> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Review> findAllByOrganizationIdAndSupervisorIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
            UUID organizationId, UUID supervisorId);

    long countByOrganizationIdAndSupervisorIdAndCompletedAtIsNotNull(UUID organizationId, UUID supervisorId);

    /** Completed reviews (submitted supervision) for reporting. */
    List<Review> findAllByOrganizationIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(UUID organizationId);
}
