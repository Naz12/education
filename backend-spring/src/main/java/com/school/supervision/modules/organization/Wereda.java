package com.school.supervision.modules.organization;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "weredas")
public class Wereda extends TenantScopedEntity {
    @Column(name = "subcity_id", nullable = false)
    private UUID subcityId;
    @Column(nullable = false, length = 255)
    private String name;

    public UUID getSubcityId() {
        return subcityId;
    }

    public void setSubcityId(UUID subcityId) {
        this.subcityId = subcityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
