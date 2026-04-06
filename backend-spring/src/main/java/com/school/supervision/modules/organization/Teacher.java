package com.school.supervision.modules.organization;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "teachers")
public class Teacher extends TenantScopedEntity {
    @Column(name = "school_id", nullable = false)
    private UUID schoolId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(nullable = false)
    private String name;
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    public UUID getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(UUID schoolId) {
        this.schoolId = schoolId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }
}
