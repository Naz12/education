package com.school.supervision.modules.reports;

import com.school.supervision.common.tenant.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends TenantAwareRepository<AuditLog, UUID> {
}
