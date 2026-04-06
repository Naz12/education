package com.school.supervision.modules.assignments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.modules.checklists.Checklist;
import com.school.supervision.modules.checklists.GradeGroupRepository;
import com.school.supervision.modules.organization.School;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Derives the grade scope for an assignment from school supported grades and checklist grade group.
 */
public final class AssignmentGradeScope {
    private AssignmentGradeScope() {}

    public static Set<String> resolve(
            ObjectMapper objectMapper,
            School school,
            Checklist checklist,
            GradeGroupRepository gradeGroupRepository
    ) {
        Set<String> schoolGrades = GradeCodes.normalize(
                GradeCodes.parseJsonArray(objectMapper, school.getSupportedGradeCodesJson()));
        Set<String> checklistGrades = Set.of();
        if (checklist.getGradeGroupId() != null) {
            UUID orgId = checklist.getOrganizationId();
            checklistGrades = gradeGroupRepository
                    .findByIdAndOrganizationId(checklist.getGradeGroupId(), orgId)
                    .map(gg -> GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, gg.getGradeCodesJson())))
                    .orElse(Set.of());
        }
        if (!schoolGrades.isEmpty() && !checklistGrades.isEmpty()) {
            HashSet<String> inter = new HashSet<>(schoolGrades);
            inter.retainAll(checklistGrades);
            return inter;
        }
        if (!schoolGrades.isEmpty()) {
            return schoolGrades;
        }
        if (!checklistGrades.isEmpty()) {
            return checklistGrades;
        }
        return Set.of();
    }
}
