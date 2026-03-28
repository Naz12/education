package com.school.supervision.common.tenant;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantGuard {
    public void assertSameTenant(UUID resourceOrganizationId) {
        UUID current = TenantContext.getOrganizationId();
        if (current == null || !current.equals(resourceOrganizationId)) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }
    }
}
