package com.school.supervision.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }
}
