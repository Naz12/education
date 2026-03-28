package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.DomainEnums.ChecklistVersionStatus;
import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "checklist_versions")
public class ChecklistVersion extends TenantScopedEntity {
    @Column(name = "checklist_id", nullable = false)
    private UUID checklistId;
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistVersionStatus status;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public UUID getChecklistId() {
        return checklistId;
    }

    public void setChecklistId(UUID checklistId) {
        this.checklistId = checklistId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public ChecklistVersionStatus getStatus() {
        return status;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public void setStatus(ChecklistVersionStatus status) {
        this.status = status;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
