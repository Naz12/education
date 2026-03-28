package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.DomainEnums.ChecklistItemType;
import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "checklist_items")
public class ChecklistItem extends TenantScopedEntity {
    @Column(name = "checklist_version_id", nullable = false)
    private UUID checklistVersionId;
    @Column(nullable = false)
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_localized_json", columnDefinition = "jsonb")
    private String questionLocalizedJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ChecklistItemType itemType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", columnDefinition = "jsonb")
    private String optionsJson;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_json", columnDefinition = "jsonb")
    private String validationJson;
    @Column(name = "group_key")
    private String groupKey;
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public UUID getChecklistVersionId() {
        return checklistVersionId;
    }

    public void setChecklistVersionId(UUID checklistVersionId) {
        this.checklistVersionId = checklistVersionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getQuestionLocalizedJson() {
        return questionLocalizedJson;
    }

    public void setQuestionLocalizedJson(String questionLocalizedJson) {
        this.questionLocalizedJson = questionLocalizedJson;
    }

    public ChecklistItemType getItemType() {
        return itemType;
    }

    public void setItemType(ChecklistItemType itemType) {
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

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
