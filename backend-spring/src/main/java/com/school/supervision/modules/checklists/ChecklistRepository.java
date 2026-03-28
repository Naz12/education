package com.school.supervision.modules.checklists;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChecklistRepository extends TenantAwareRepository<Checklist, UUID> {
}
