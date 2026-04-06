package com.school.supervision.modules.organization;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "clusters")
public class Cluster extends TenantScopedEntity {
    @Column(name = "wereda_id", nullable = false)
    private UUID weredaId;
    @Column(nullable = false, length = 255)
    private String name;

    public UUID getWeredaId() {
        return weredaId;
    }

    public void setWeredaId(UUID weredaId) {
        this.weredaId = weredaId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
