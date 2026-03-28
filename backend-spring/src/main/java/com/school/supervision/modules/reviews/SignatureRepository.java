package com.school.supervision.modules.reviews;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SignatureRepository extends TenantAwareRepository<Signature, UUID> {
    java.util.List<Signature> findAllByReviewIdAndOrganizationId(UUID reviewId, UUID organizationId);
}
