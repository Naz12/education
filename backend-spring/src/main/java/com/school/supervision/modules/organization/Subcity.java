package com.school.supervision.modules.organization;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "subcities")
public class Subcity extends TenantScopedEntity {
    @Column(name = "city_id", nullable = false)
    private UUID cityId;
    @Column(nullable = false, length = 255)
    private String name;

    public UUID getCityId() {
        return cityId;
    }

    public void setCityId(UUID cityId) {
        this.cityId = cityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
