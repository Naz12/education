package com.school.supervision.modules.organization;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TeacherRepository extends TenantAwareRepository<Teacher, UUID> {
    List<Teacher> findAllByOrganizationIdAndSchoolIdIn(UUID organizationId, Collection<UUID> schoolIds);

    List<Teacher> findAllByOrganizationIdAndSchoolId(UUID organizationId, UUID schoolId);
}
