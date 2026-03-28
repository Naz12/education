package com.school.supervision.integration;

import com.school.supervision.common.tenant.TenantContext;
import com.school.supervision.common.tenant.TenantGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

class TenantGuardTest {
    private final TenantGuard tenantGuard = new TenantGuard();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void allowsWhenTenantMatches() {
        UUID org = UUID.randomUUID();
        TenantContext.setOrganizationId(org);
        tenantGuard.assertSameTenant(org);
    }

    @Test
    void blocksCrossTenantAccess() {
        TenantContext.setOrganizationId(UUID.randomUUID());
        Assertions.assertThrows(AccessDeniedException.class,
                () -> tenantGuard.assertSameTenant(UUID.randomUUID()));
    }
}
