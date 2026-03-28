package com.school.supervision.modules.users;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.Collection;

@Repository
public interface UserRepository extends TenantAwareRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameAndOrganizationId(String username, UUID organizationId);
    List<User> findAllByOrganizationIdAndCoordinatorUserId(UUID organizationId, UUID coordinatorUserId);
    List<User> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE u.organizationId = :orgId AND r.name = 'SUPERVISOR'")
    List<User> findSupervisorsInOrganization(@Param("orgId") UUID organizationId);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE u.organizationId = :orgId AND r.name = 'SUPERVISOR' AND u.coordinatorUserId = :coordId")
    List<User> findSupervisorsForCoordinator(@Param("orgId") UUID organizationId, @Param("coordId") UUID coordinatorUserId);
}
