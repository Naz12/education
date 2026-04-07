package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.DomainEnums.TargetType;
import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "checklist_target_options")
public class ChecklistTargetOption extends TenantScopedEntity {
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_kind", nullable = false)
    private TargetType routingKind;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TargetType getRoutingKind() {
        return routingKind;
    }

    public void setRoutingKind(TargetType routingKind) {
        this.routingKind = routingKind;
    }
}
