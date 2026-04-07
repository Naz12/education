package com.school.supervision.modules.assignments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.supervision.common.domain.DomainEnums.AssignmentStatus;
import com.school.supervision.common.domain.DomainEnums.TargetType;
import com.school.supervision.common.grades.GradeCodes;
import com.school.supervision.modules.checklists.Checklist;
import com.school.supervision.modules.checklists.GradeGroup;
import com.school.supervision.modules.checklists.GradeGroupRepository;
import com.school.supervision.modules.organization.School;
import com.school.supervision.modules.organization.SchoolRepository;
import com.school.supervision.modules.organization.Teacher;
import com.school.supervision.modules.organization.TeacherRepository;
import com.school.supervision.modules.reports.AuditService;
import com.school.supervision.modules.users.User;
import com.school.supervision.modules.users.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssignmentAutoAssignmentService {
    private static final EnumSet<AssignmentStatus> OPEN = EnumSet.of(AssignmentStatus.PENDING, AssignmentStatus.IN_PROGRESS);

    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final GradeGroupRepository gradeGroupRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AssignmentAutoAssignmentService(SchoolRepository schoolRepository,
                                           TeacherRepository teacherRepository,
                                           AssignmentRepository assignmentRepository,
                                           UserRepository userRepository,
                                           GradeGroupRepository gradeGroupRepository,
                                           AuditService auditService,
                                           ObjectMapper objectMapper) {
        this.schoolRepository = schoolRepository;
        this.teacherRepository = teacherRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.gradeGroupRepository = gradeGroupRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int assignAfterPublish(UUID organizationId,
                                  Checklist checklist,
                                  UUID publishedVersionId,
                                  UUID actorUserId) {
        if (!checklist.isAutoAssignOnPublish()) {
            return 0;
        }
        TargetType targetType = checklist.getTargetType();
        if (targetType == null
                || targetType == TargetType.SCHOOL_STAFF) {
            // SCHOOL_STAFF has no stable roster per school in this schema; assign manually.
            return 0;
        }
        if (checklist.getGradeGroupId() == null) {
            return 0;
        }
        GradeGroup gg = gradeGroupRepository.findByIdAndOrganizationId(checklist.getGradeGroupId(), organizationId)
                .orElse(null);
        if (gg == null) {
            return 0;
        }
        Set<String> checklistGrades = GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, gg.getGradeCodesJson()));
        if (checklistGrades.isEmpty()) {
            return 0;
        }

        List<User> supervisors = listSupervisors(organizationId, checklist.getCoordinatorUserId());
        if (supervisors.isEmpty()) {
            return 0;
        }

        if (targetType == TargetType.TEACHER) {
            return assignTeacherTargets(organizationId, checklist, publishedVersionId, actorUserId, checklistGrades, supervisors);
        }
        if (targetType == TargetType.SCHOOL || targetType == TargetType.DIRECTOR) {
            return assignSchoolOrDirectorTargets(organizationId, checklist, publishedVersionId, actorUserId, checklistGrades, supervisors);
        }
        return 0;
    }

    /** One assignment per school (school-level or director visit at that school). */
    private int assignSchoolOrDirectorTargets(UUID organizationId,
                                              Checklist checklist,
                                              UUID publishedVersionId,
                                              UUID actorUserId,
                                              Set<String> checklistGrades,
                                              List<User> supervisors) {
        List<School> schools = listSchoolsInScope(organizationId, checklist.getCoordinatorUserId());
        int created = 0;
        for (School school : schools) {
            Set<String> schoolGrades = GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, school.getSupportedGradeCodesJson()));
            if (schoolGrades.isEmpty()) {
                continue;
            }
            if (!GradeCodes.overlaps(checklistGrades, schoolGrades)) {
                continue;
            }
            if (assignmentRepository.existsByOrganizationIdAndChecklistIdAndSchoolIdAndStatusIn(
                    organizationId, checklist.getId(), school.getId(), OPEN)) {
                continue;
            }
            User supervisor = pickSupervisor(supervisors, school, checklist, organizationId);
            if (supervisor == null) {
                continue;
            }
            Assignment a = new Assignment();
            a.setOrganizationId(organizationId);
            a.setChecklistId(checklist.getId());
            a.setChecklistVersionId(publishedVersionId);
            a.setSupervisorId(supervisor.getId());
            a.setTargetType(checklist.getTargetType());
            a.setSchoolId(school.getId());
            a.setTeacherId(null);
            a.setStaffUserId(null);
            a.setDueDate(null);
            a.setStatus(AssignmentStatus.PENDING);
            a.setCreatedBy(actorUserId);
            assignmentRepository.save(a);
            created++;
            auditService.record(
                    organizationId,
                    actorUserId,
                    "ASSIGNMENT_AUTO_CREATED",
                    "ASSIGNMENT",
                    a.getId(),
                    java.util.Map.of(
                            "checklistId", checklist.getId().toString(),
                            "schoolId", school.getId().toString(),
                            "supervisorId", supervisor.getId().toString()
                    )
            );
        }
        return created;
    }

    /** One assignment per teacher when the checklist targets classroom visits. */
    private int assignTeacherTargets(UUID organizationId,
                                     Checklist checklist,
                                     UUID publishedVersionId,
                                     UUID actorUserId,
                                     Set<String> checklistGrades,
                                     List<User> supervisors) {
        List<School> schools = listSchoolsInScope(organizationId, checklist.getCoordinatorUserId());
        int created = 0;
        for (School school : schools) {
            Set<String> schoolGrades = GradeCodes.normalize(GradeCodes.parseJsonArray(objectMapper, school.getSupportedGradeCodesJson()));
            if (schoolGrades.isEmpty()) {
                continue;
            }
            if (!GradeCodes.overlaps(checklistGrades, schoolGrades)) {
                continue;
            }
            Set<String> visitScope = AssignmentGradeScope.resolve(objectMapper, school, checklist, gradeGroupRepository);
            if (visitScope.isEmpty()) {
                continue;
            }
            List<Teacher> teachers = teacherRepository.findAllByOrganizationIdAndSchoolId(organizationId, school.getId());
            for (Teacher teacher : teachers) {
                Set<String> teacherGrades = GradeCodes.normalize(
                        GradeCodes.parseJsonArray(objectMapper, teacher.getResponsibleGradeCodesJson()));
                if (!teacherGrades.isEmpty() && !GradeCodes.overlaps(visitScope, teacherGrades)) {
                    continue;
                }
                if (assignmentRepository.existsByOrganizationIdAndChecklistIdAndSchoolIdAndTeacherIdAndStatusIn(
                        organizationId, checklist.getId(), school.getId(), teacher.getId(), OPEN)) {
                    continue;
                }
                User supervisor = pickSupervisor(supervisors, school, checklist, organizationId);
                if (supervisor == null) {
                    continue;
                }
                Assignment a = new Assignment();
                a.setOrganizationId(organizationId);
                a.setChecklistId(checklist.getId());
                a.setChecklistVersionId(publishedVersionId);
                a.setSupervisorId(supervisor.getId());
                a.setTargetType(TargetType.TEACHER);
                a.setSchoolId(school.getId());
                a.setTeacherId(teacher.getId());
                a.setStaffUserId(null);
                a.setDueDate(null);
                a.setStatus(AssignmentStatus.PENDING);
                a.setCreatedBy(actorUserId);
                assignmentRepository.save(a);
                created++;
                auditService.record(
                        organizationId,
                        actorUserId,
                        "ASSIGNMENT_AUTO_CREATED",
                        "ASSIGNMENT",
                        a.getId(),
                        java.util.Map.of(
                                "checklistId", checklist.getId().toString(),
                                "schoolId", school.getId().toString(),
                                "teacherId", teacher.getId().toString(),
                                "supervisorId", supervisor.getId().toString()
                        )
                );
            }
        }
        return created;
    }

    private List<School> listSchoolsInScope(UUID organizationId, UUID checklistCoordinatorId) {
        if (checklistCoordinatorId != null) {
            return schoolRepository.findAllByOrganizationIdAndCoordinatorUserId(organizationId, checklistCoordinatorId);
        }
        return schoolRepository.findAllByOrganizationId(organizationId);
    }

    private List<User> listSupervisors(UUID organizationId, UUID checklistCoordinatorId) {
        if (checklistCoordinatorId != null) {
            return userRepository.findSupervisorsForCoordinator(organizationId, checklistCoordinatorId);
        }
        return userRepository.findSupervisorsInOrganization(organizationId);
    }

    private User pickSupervisor(List<User> supervisors, School school, Checklist checklist, UUID organizationId) {
        java.util.Set<String> scope = AssignmentGradeScope.resolve(objectMapper, school, checklist, gradeGroupRepository);
        List<User> eligible = supervisors.stream()
                .filter(u -> scope.isEmpty()
                        || GradeCodes.overlaps(u.effectiveSupervisedGrades(objectMapper), scope))
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.stream()
                .min(Comparator.comparingLong(u -> openAssignmentLoad(u.getId(), organizationId)))
                .orElse(null);
    }

    private long openAssignmentLoad(UUID supervisorId, UUID organizationId) {
        long p = assignmentRepository.countBySupervisorIdAndOrganizationIdAndStatus(supervisorId, organizationId, AssignmentStatus.PENDING);
        long i = assignmentRepository.countBySupervisorIdAndOrganizationIdAndStatus(supervisorId, organizationId, AssignmentStatus.IN_PROGRESS);
        return p + i;
    }
}
