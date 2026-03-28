package com.school.supervision.modules.checklists;

import com.school.supervision.common.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "grade_groups")
public class GradeGroup extends TenantScopedEntity {
    @Column(name = "coordinator_user_id")
    private UUID coordinatorUserId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "grades_description", nullable = false, length = 500)
    private String gradesDescription;

    /** JSON array of canonical grade codes for matching (checkbox selection). */
    @Column(name = "grade_codes", columnDefinition = "TEXT")
    private String gradeCodesJson = "[]";

    public String getGradeCodesJson() {
        return gradeCodesJson;
    }

    public void setGradeCodesJson(String gradeCodesJson) {
        this.gradeCodesJson = gradeCodesJson == null ? "[]" : gradeCodesJson;
    }

    public UUID getCoordinatorUserId() {
        return coordinatorUserId;
    }

    public void setCoordinatorUserId(UUID coordinatorUserId) {
        this.coordinatorUserId = coordinatorUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGradesDescription() {
        return gradesDescription;
    }

    public void setGradesDescription(String gradesDescription) {
        this.gradesDescription = gradesDescription;
    }
}
