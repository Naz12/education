package com.school.supervision.common.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface TenantAwareRepository<T, ID> extends JpaRepository<T, ID> {
    List<T> findAllByOrganizationId(UUID organizationId);
    Optional<T> findByIdAndOrganizationId(ID id, UUID organizationId);
}
