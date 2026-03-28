package com.school.supervision.modules.reports;

import java.time.Instant;
import java.util.UUID;

/** One completed review row for the reports index (PDF is loaded separately). */
public record SubmittedReportSummary(
        UUID reviewId,
        UUID assignmentId,
        UUID supervisorId,
        String supervisorFullName,
        String supervisorUsername,
        Instant startedAt,
        Instant completedAt,
        String targetType,
        UUID schoolId,
        String schoolName,
        UUID teacherId,
        String teacherName,
        UUID staffUserId,
        String staffFullName,
        UUID checklistId,
        String checklistTitle,
        String locationStatus,
        Double distanceFromSchoolMeters
) {}
