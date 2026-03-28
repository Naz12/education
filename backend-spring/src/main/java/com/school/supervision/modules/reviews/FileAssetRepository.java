package com.school.supervision.modules.reviews;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FileAssetRepository extends TenantAwareRepository<FileAsset, UUID> {
    java.util.Optional<FileAsset> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
