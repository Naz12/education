package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.DomainEnums;
import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "checklist_item_type_defaults")
public class ChecklistItemTypeDefaults extends TenantScopedEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private DomainEnums.ChecklistItemType itemType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", columnDefinition = "jsonb")
    private String optionsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_json", columnDefinition = "jsonb")
    private String validationJson;

    public DomainEnums.ChecklistItemType getItemType() {
        return itemType;
    }

    public void setItemType(DomainEnums.ChecklistItemType itemType) {
        this.itemType = itemType;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public String getValidationJson() {
        return validationJson;
    }

    public void setValidationJson(String validationJson) {
        this.validationJson = validationJson;
    }
}

