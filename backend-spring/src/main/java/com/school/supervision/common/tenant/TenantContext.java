package com.school.supervision.common.tenant;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOrganizationId(UUID organizationId) {
        TENANT.set(organizationId);
    }

    public static UUID getOrganizationId() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}
