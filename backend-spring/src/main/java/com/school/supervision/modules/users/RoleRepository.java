package com.school.supervision.modules.users;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends TenantAwareRepository<Role, UUID> {
    Optional<Role> findByOrganizationIdAndName(UUID organizationId, String name);
}
