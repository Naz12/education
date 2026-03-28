package com.school.supervision.modules.reviews;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface ReviewAnswerRepository extends TenantAwareRepository<ReviewAnswer, UUID> {
    java.util.List<ReviewAnswer> findAllByReviewIdAndOrganizationId(UUID reviewId, UUID organizationId);
    boolean existsByOrganizationIdAndChecklistItemIdIn(UUID organizationId, Collection<UUID> checklistItemIds);
}
