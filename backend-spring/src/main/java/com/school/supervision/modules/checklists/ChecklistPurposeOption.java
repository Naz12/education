package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "checklist_purpose_options")
public class ChecklistPurposeOption extends TenantScopedEntity {
    @Column(nullable = false)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
