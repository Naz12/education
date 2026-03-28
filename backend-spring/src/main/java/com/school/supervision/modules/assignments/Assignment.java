package com.school.supervision.modules.assignments;

import com.school.supervision.common.domain.DomainEnums.AssignmentStatus;
import com.school.supervision.common.domain.DomainEnums.TargetType;
import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assignments")
public class Assignment extends TenantScopedEntity {
    @Column(name = "checklist_id", nullable = false)
    private UUID checklistId;
    @Column(name = "checklist_version_id", nullable = false)
    private UUID checklistVersionId;
    @Column(name = "supervisor_id", nullable = false)
    private UUID supervisorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;
    @Column(name = "school_id")
    private UUID schoolId;
    @Column(name = "teacher_id")
    private UUID teacherId;
    /** User id for non-teacher school roles (registrar, counselor, etc.); not used for DIRECTOR (use school only). */
    @Column(name = "staff_user_id")
    private UUID staffUserId;
    @Column(name = "due_date")
    private Instant dueDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public UUID getChecklistId() {
        return checklistId;
    }

    public void setChecklistId(UUID checklistId) {
        this.checklistId = checklistId;
    }

    public UUID getChecklistVersionId() {
        return checklistVersionId;
    }

    public void setChecklistVersionId(UUID checklistVersionId) {
        this.checklistVersionId = checklistVersionId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(UUID schoolId) {
        this.schoolId = schoolId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(UUID teacherId) {
        this.teacherId = teacherId;
    }

    public UUID getStaffUserId() {
        return staffUserId;
    }

    public void setStaffUserId(UUID staffUserId) {
        this.staffUserId = staffUserId;
    }

    public UUID getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(UUID supervisorId) {
        this.supervisorId = supervisorId;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
