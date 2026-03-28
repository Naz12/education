package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.DomainEnums.DisplayMode;
import com.school.supervision.common.domain.DomainEnums.ChecklistPurpose;
import com.school.supervision.common.domain.DomainEnums.TargetType;
import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "checklists")
public class Checklist extends TenantScopedEntity {
    @Column(nullable = false)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;
    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode", nullable = false)
    private DisplayMode displayMode;
    @Enumerated(EnumType.STRING)
    private ChecklistPurpose purpose;
    @Column(name = "grade_scope")
    private String gradeScope;
    @Column(name = "grade_group_id")
    private UUID gradeGroupId;
    @Column(name = "coordinator_user_id")
    private UUID coordinatorUserId;
    @Column(name = "active_version")
    private Integer activeVersion;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "auto_assign_on_publish", nullable = false)
    private boolean autoAssignOnPublish = true;

    public boolean isAutoAssignOnPublish() {
        return autoAssignOnPublish;
    }

    public void setAutoAssignOnPublish(boolean autoAssignOnPublish) {
        this.autoAssignOnPublish = autoAssignOnPublish;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public Integer getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(Integer activeVersion) {
        this.activeVersion = activeVersion;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public ChecklistPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(ChecklistPurpose purpose) {
        this.purpose = purpose;
    }

    public String getGradeScope() {
        return gradeScope;
    }

    public void setGradeScope(String gradeScope) {
        this.gradeScope = gradeScope;
    }

    public UUID getGradeGroupId() {
        return gradeGroupId;
    }

    public void setGradeGroupId(UUID gradeGroupId) {
        this.gradeGroupId = gradeGroupId;
    }

    public UUID getCoordinatorUserId() {
        return coordinatorUserId;
    }

    public void setCoordinatorUserId(UUID coordinatorUserId) {
        this.coordinatorUserId = coordinatorUserId;
    }
}
