package com.school.supervision.modules.assignments;

import com.school.supervision.common.domain.DomainEnums.AssignmentStatus;
import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends TenantAwareRepository<Assignment, UUID> {
    List<Assignment> findAllBySupervisorIdAndOrganizationId(UUID supervisorId, UUID organizationId);
    boolean existsByOrganizationIdAndSupervisorId(UUID organizationId, UUID supervisorId);
    boolean existsByOrganizationIdAndTeacherId(UUID organizationId, UUID teacherId);
    boolean existsByOrganizationIdAndStaffUserId(UUID organizationId, UUID staffUserId);
    boolean existsByOrganizationIdAndSchoolId(UUID organizationId, UUID schoolId);
    List<Assignment> findAllByOrganizationIdAndCreatedBy(UUID organizationId, UUID createdBy);
    boolean existsByOrganizationIdAndChecklistId(UUID organizationId, UUID checklistId);

    long countBySupervisorIdAndOrganizationIdAndStatus(UUID supervisorId, UUID organizationId, AssignmentStatus status);

    boolean existsByOrganizationIdAndChecklistIdAndSchoolIdAndStatusIn(
            UUID organizationId,
            UUID checklistId,
            UUID schoolId,
            Collection<AssignmentStatus> statuses);

    boolean existsByOrganizationIdAndChecklistIdAndSchoolIdAndTeacherIdAndStatusIn(
            UUID organizationId,
            UUID checklistId,
            UUID schoolId,
            UUID teacherId,
            Collection<AssignmentStatus> statuses);

    boolean existsByOrganizationIdAndChecklistIdAndSchoolIdAndTeacherIdAndTargetGradeCodeAndStatusIn(
            UUID organizationId,
            UUID checklistId,
            UUID schoolId,
            UUID teacherId,
            String targetGradeCode,
            Collection<AssignmentStatus> statuses);
}
