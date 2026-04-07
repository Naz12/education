package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChecklistRepository extends TenantAwareRepository<Checklist, UUID> {
    @EntityGraph(attributePaths = {"targetOption", "purposeOption"})
    @Query("SELECT c FROM Checklist c WHERE c.organizationId = :orgId")
    List<Checklist> findAllByOrganizationIdWithOptions(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(c) FROM Checklist c WHERE c.organizationId = :orgId AND c.targetOption.id = :optionId")
    long countByOrganizationIdAndTargetOptionId(@Param("orgId") UUID orgId, @Param("optionId") UUID optionId);

    @Query("SELECT COUNT(c) FROM Checklist c WHERE c.organizationId = :orgId AND c.purposeOption.id = :optionId")
    long countByOrganizationIdAndPurposeOptionId(@Param("orgId") UUID orgId, @Param("optionId") UUID optionId);
}
