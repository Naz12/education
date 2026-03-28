package com.school.supervision.modules.users;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role extends TenantScopedEntity {
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(name = "is_system_role", nullable = false)
    private boolean systemRole;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSystemRole(boolean systemRole) {
        this.systemRole = systemRole;
    }
}
