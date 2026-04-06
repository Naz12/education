package com.school.supervision.modules.organization;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cities")
public class City extends TenantScopedEntity {
    @Column(nullable = false, length = 255)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
